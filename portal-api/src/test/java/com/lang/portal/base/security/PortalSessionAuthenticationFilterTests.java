package com.lang.portal.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class PortalSessionAuthenticationFilterTests {

  private final NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
  private final PortalSessionAuthenticationFilter filter = new PortalSessionAuthenticationFilter(
      new PortalCommonProperties(), client, new NewApiCookiePolicy(new PortalCommonProperties()));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void missingOrIncompleteCookiesLeaveRequestAnonymousWithoutUpstreamCall() throws Exception {
    for (Cookie[] cookies : new Cookie[][] {
        null,
        {new Cookie("LANG_SESSION", "upstream-session")},
        {new Cookie("LANG_UID", "42")},
        {new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "not-a-number")},
        {new Cookie("LANG_SESSION", "one"), new Cookie("LANG_SESSION", "two"), new Cookie("LANG_UID", "42")}
    }) {
      MockHttpServletRequest request = request(cookies);
      filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
          assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    verifyNoInteractions(client);
  }

  @Test
  void validatedSessionCreatesOnlyOrdinaryUserAuthentication() throws Exception {
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));

    filter.doFilter(request(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42")),
        new MockHttpServletResponse(), (req, res) -> {
          var authentication = SecurityContextHolder.getContext().getAuthentication();
          assertThat(authentication).isNotNull();
          assertThat(authentication.getName()).isEqualTo("ordinary");
          assertThat(authentication.getAuthorities()).extracting(authority -> authority.getAuthority())
              .containsExactly("ROLE_USER");
        });

    verify(client).currentUser(new NewApiSession("upstream-session", 42L));
  }

  @Test
  void forgedNewApiUserHeaderIsIgnoredInFavorOfValidatedCookies() throws Exception {
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", null, null));
    MockHttpServletRequest request = request(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    request.addHeader("New-Api-User", "999");

    filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ordinary"));

    verify(client).currentUser(new NewApiSession("upstream-session", 42L));
  }

  @Test
  void upstreamUnauthorizedLeavesRequestAnonymous() throws Exception {
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenThrow(new PortalException(PortalErrorCode.UNAUTHENTICATED));

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42")),
        response, (req, res) ->
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

    assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
    assertThat(response.getHeaders("Set-Cookie").get(0)).contains("LANG_SESSION=", "Max-Age=0");
    assertThat(response.getHeaders("Set-Cookie").get(1)).contains("LANG_UID=", "Max-Age=0");
  }

  @Test
  void upstreamFailureIsNotReclassifiedAsAnonymousSession() {
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenThrow(new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(
        request(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42")),
        new MockHttpServletResponse(), (req, res) -> {}))
        .isInstanceOf(UpstreamException.class)
        .matches(error -> ((UpstreamException) error).errorCode() == PortalErrorCode.UPSTREAM_UNAVAILABLE);
  }

  private MockHttpServletRequest request(Cookie... cookies) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/portal/api/test-protected");
    request.setCookies(cookies);
    return request;
  }
}
