package com.lang.portal.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.session.PortalSessionRecord;
import com.lang.portal.infrastructure.session.PortalSessionStore;
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

class PortalSessionRevocationFilterTests {

  private final NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
  private final PortalSessionStore store = mock(PortalSessionStore.class);
  private final PortalSessionAuthenticationFilter filter =
      new PortalSessionAuthenticationFilter(
          new PortalCommonProperties(),
          client,
          new NewApiCookiePolicy(new PortalCommonProperties()),
          store);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void unknownServerSideSessionIsRejectedWithoutUpstreamCall() throws Exception {
    // 会话 ID 在服务端不存在（含退出后的旧 Cookie 与旧格式 Cookie），不得访问上游。
    when(store.find("unknown-session-id")).thenReturn(null);

    filter.doFilter(
        request("unknown-session-id", "42"),
        new MockHttpServletResponse(),
        (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

    verify(client, never()).currentUser(any());
  }

  @Test
  void liveServerSideSessionResolvesUpstreamCredentialsFromTheRecord() throws Exception {
    // 上游凭证必须来自服务端记录，不能再由浏览器 Cookie 直接提供。
    when(store.find("live-session-id"))
        .thenReturn(new PortalSessionRecord("live-session-id", "upstream-session", 42L));
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", null));

    filter.doFilter(
        request("live-session-id", "42"),
        new MockHttpServletResponse(),
        (req, res) -> {
          assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ordinary");
          assertThat(req.getAttribute(PortalSessionAuthenticationFilter.UPSTREAM_SESSION_ATTRIBUTE))
              .isEqualTo(new NewApiSession("upstream-session", 42L));
        });

    verify(client).currentUser(new NewApiSession("upstream-session", 42L));
  }

  @Test
  void tamperedUserIdCookieIsRejectedEvenWhenTheServerRecordExists() throws Exception {
    when(store.find("live-session-id"))
        .thenReturn(new PortalSessionRecord("live-session-id", "upstream-session", 42L));

    filter.doFilter(
        request("live-session-id", "999"),
        new MockHttpServletResponse(),
        (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

    verify(client, never()).currentUser(any());
  }

  @Test
  void storeFailureIsReportedAsAServiceFailureRatherThanAnInvalidSession() throws Exception {
    // 存储不可用时不得放行，也不得伪装成普通未登录（那会让全部在线用户被静默登出）。
    when(store.find("live-session-id")).thenThrow(new IllegalStateException("redis down"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] chained = {false};

    filter.doFilter(
        request("live-session-id", "42"),
        response,
        (req, res) -> chained[0] = true);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("UPSTREAM_UNAVAILABLE");
    // 请求不得继续进入业务处理。
    assertThat(chained[0]).isFalse();
  }

  private MockHttpServletRequest request(String sessionId, String userId) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/portal/api/test-protected");
    request.setCookies(new Cookie("LANG_SESSION", sessionId), new Cookie("LANG_UID", userId));
    return request;
  }
}