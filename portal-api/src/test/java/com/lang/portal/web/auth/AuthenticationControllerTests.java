package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.base.exception.PortalErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthenticationControllerTests {

  @Test
  void loginSetsOnlyTheTwoHostOnlyPortalSessionCookiesAndReturnsMinimalProfile() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    when(client.login(new NewApiCredentials("ordinary", "correct-horse"))).thenReturn(new NewApiLoginResult(
        new NewApiSession("upstream-session", 42L),
        new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test")));
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, client),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));
    MockHttpServletRequest request = validCsrfRequest();

    var response = controller.login(new LoginRequest("ordinary", "correct-horse"), request);

    assertThat(response.getBody().data()).isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains("LANG_SESSION=upstream-session", "HttpOnly", "Path=/portal");
    assertThat(response.getHeaders().get("Set-Cookie").get(1)).contains("LANG_UID=42", "HttpOnly", "Path=/portal");
  }

  @Test
  void profileAndRefreshReuseTheAlreadyValidatedPortalIdentity() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, mock(NewApiAuthenticationClient.class)),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));
    PortalAuthenticatedUser user = new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");

    assertThat(controller.profile(user, new MockHttpServletRequest()).getBody().data())
        .isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    assertThat(controller.refresh(user, validCsrfRequest()).getBody().data())
        .isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
  }

  @Test
  void logoutIsIdempotentWithoutSessionAndExpiresAnyPortalSessionCookies() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, mock(NewApiAuthenticationClient.class)),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));

    var response = controller.logout(validCsrfRequest());

    assertThat(response.getBody().data()).isNull();
    assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    assertThat(response.getHeaders().get("Set-Cookie").get(0)).contains("LANG_SESSION=", "Max-Age=0");
    assertThat(response.getHeaders().get("Set-Cookie").get(1)).contains("LANG_UID=", "Max-Age=0");
  }

  @Test
  void logoutRevokesValidSessionThenAlwaysExpiresItsPortalCookies() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, client),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));
    MockHttpServletRequest request = validCsrfRequest();
    request.setCookies(
        new Cookie("XSRF-TOKEN", "csrf-token"),
        new Cookie("LANG_SESSION", "upstream-session"),
        new Cookie("LANG_UID", "42"));

    var response = controller.logout(request);

    verify(client).logout(new NewApiSession("upstream-session", 42L));
    assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
  }

  @Test
  void rateLimitedRegistrationDoesNotCallTheUpstreamClient() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(true);
    properties.auth().setAllowedOrigins("http://portal.test");
    properties.auth().rateLimit().registration().setClientAttempts(1);
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, client),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));

    var firstResponse = controller.register(
        new RegisterRequest("ordinary", "correct-horse", "correct-horse"), validCsrfRequest());
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        controller.register(new RegisterRequest("another", "correct-horse", "correct-horse"), validCsrfRequest()))
        .isInstanceOf(com.lang.portal.base.exception.PortalException.class)
        .matches(error -> ((com.lang.portal.base.exception.PortalException) error).errorCode()
            == com.lang.portal.base.exception.PortalErrorCode.RATE_LIMITED);
    verify(client).register(new NewApiCredentials("ordinary", "correct-horse"));
    assertThat(firstResponse.getHeaders().get("Set-Cookie")).isNull();
  }

  @Test
  void logoutPreservesUpstreamFailureButRegistersCookieExpiryForTheErrorResponse() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    doThrow(new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE))
        .when(client).logout(new NewApiSession("upstream-session", 42L));
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, client),
        new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties),
        properties,
        limiter(properties));
    MockHttpServletRequest request = validCsrfRequest();
    request.setCookies(
        new Cookie("XSRF-TOKEN", "csrf-token"),
        new Cookie("LANG_SESSION", "upstream-session"),
        new Cookie("LANG_UID", "42"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.logout(request))
        .isInstanceOf(UpstreamException.class);
    assertThat(request.getAttribute("portal.auth.expire-session-cookies"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .hasSize(2);
    var errorResponse = new com.lang.portal.base.exception.GlobalExceptionHandler(
        mock(com.lang.portal.infrastructure.web.PortalApiNotFoundHandler.class))
        .handlePortal(new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE), request);
    assertThat(errorResponse.getStatusCode().value()).isEqualTo(503);
    assertThat(errorResponse.getHeaders().get("Set-Cookie")).hasSize(2);
  }

  private AuthenticationRateLimiter limiter(PortalCommonProperties properties) {
    return new AuthenticationRateLimiter(properties, new PortalClientAddressResolver(properties));
  }

  private MockHttpServletRequest validCsrfRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("XSRF-TOKEN", "csrf-token"));
    request.addHeader("X-XSRF-TOKEN", "csrf-token");
    request.addHeader("Origin", "http://portal.test");
    return request;
  }
}
