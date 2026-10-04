package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.infrastructure.session.InMemoryPortalSessionStore;
import com.lang.portal.infrastructure.session.PortalSessionRecord;
import com.lang.portal.infrastructure.session.PortalSessionStore;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 服务端会话撤销的端到端契约：上游退出返回成功并不等于旧 Cookie 失效，
 * 撤销必须由 Lang API 自己的会话记录保证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutRevokesServerSideSessionTests {

  private static final NewApiUserProfile USER =
      new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private NewApiAuthenticationClient client;
  @MockitoBean private PortalSessionStore sessionStore;
  @MockitoBean private com.lang.portal.web.legal.LegalContentService legalContent;

  @AfterEach
  void resetMocks() {
    doReturn(null).when(sessionStore).find(any());
  }

  @Test
  void logoutRevokesTheServerSideRecordAndOldCookieStopsWorking() throws Exception {
    Csrf csrf = csrf();
    doReturn(new NewApiLoginResult(new NewApiSession("upstream-session", 42L), USER))
        .when(client).login(new NewApiCredentials("ordinary", "correct-horse"));
    String sessionId = "server-side-session-id";
    doReturn(sessionId).when(sessionStore).create("upstream-session", 42L);

    MvcResult login = mvc.perform(authPost("/portal/api/auth/login", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\"}"))
        .andExpect(status().isOk())
        .andReturn();
    String issuedCookie = sessionCookie(login);
    assertThat(issuedCookie).isEqualTo(sessionId);
    // 浏览器持有的必须是服务端签发的不透明标识，而不是上游会话值。
    assertThat(issuedCookie).doesNotContain("upstream-session");

    doReturn(new PortalSessionRecord(sessionId, "upstream-session", 42L)).when(sessionStore).find(sessionId);
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-session", 42L));
    Cookie[] oldCookies = {new Cookie("LANG_SESSION", sessionId), new Cookie("LANG_UID", "42")};
    mvc.perform(get("/portal/api/profile").cookie(oldCookies))
        .andExpect(status().isOk());

    doReturn(true).when(sessionStore).delete(sessionId);
    mvc.perform(authPost("/portal/api/auth/logout", csrf).cookie(oldCookies))
        .andExpect(status().isOk());
    verify(sessionStore).delete(sessionId);
    verify(client).logout(new NewApiSession("upstream-session", 42L));

    // 退出后：即使上游仍然接受旧凭证，Redis 记录已被删除，重放必须 401。
    doReturn(null).when(sessionStore).find(sessionId);
    mvc.perform(get("/portal/api/profile").cookie(oldCookies))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void upstreamStillAcceptingTheSessionDoesNotMatterAfterLogout() throws Exception {
    // 上游 /api/user/self 依然返回 200（冻结版 New API 的已知行为），Portal 仍须拒绝。
    Csrf csrf = csrf();
    String sessionId = "revoked-session-id";
    doReturn(new PortalSessionRecord(sessionId, "upstream-session", 42L))
        .when(sessionStore).find(sessionId);
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-session", 42L));
    Cookie[] revokedCookies = {new Cookie("LANG_SESSION", sessionId), new Cookie("LANG_UID", "42")};

    mvc.perform(get("/portal/api/profile").cookie(revokedCookies))
        .andExpect(status().isOk());

    doReturn(null).when(sessionStore).find(sessionId);

    mvc.perform(get("/portal/api/profile").cookie(revokedCookies))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void legacyCookieCarryingTheUpstreamSessionValueIsRejectedWithoutAnyCompatibilityPath() throws Exception {
    // 旧格式 Cookie 直接携带上游 session 值，服务端没有对应记录，必须拒绝且不得回查上游。
    doReturn(null).when(sessionStore).find("legacy-upstream-session-value");

    mvc.perform(get("/portal/api/profile")
            .cookie(new Cookie("LANG_SESSION", "legacy-upstream-session-value"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isUnauthorized());

    verify(client, never()).currentUser(any());
  }

  @Test
  void secondLoginIsUnaffectedByRevokingAnEarlierSessionOfTheSameUser() throws Exception {
    Csrf csrf = csrf();
    doReturn(new NewApiLoginResult(new NewApiSession("upstream-1", 42L), USER))
        .when(client).login(new NewApiCredentials("ordinary", "correct-horse"));
    doReturn("session-one").when(sessionStore).create("upstream-1", 42L);
    mvc.perform(authPost("/portal/api/auth/login", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\"}"))
        .andExpect(status().isOk());

    doReturn(new NewApiLoginResult(new NewApiSession("upstream-2", 42L), USER))
        .when(client).login(new NewApiCredentials("ordinary", "correct-horse"));
    doReturn("session-two").when(sessionStore).create("upstream-2", 42L);
    mvc.perform(authPost("/portal/api/auth/login", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\"}"))
        .andExpect(status().isOk());

    doReturn(new PortalSessionRecord("session-one", "upstream-1", 42L)).when(sessionStore).find("session-one");
    doReturn(new PortalSessionRecord("session-two", "upstream-2", 42L)).when(sessionStore).find("session-two");
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-1", 42L));
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-2", 42L));

    doReturn(true).when(sessionStore).delete("session-one");
    mvc.perform(authPost("/portal/api/auth/logout", csrf)
            .cookie(new Cookie("LANG_SESSION", "session-one"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isOk());

    doReturn(null).when(sessionStore).find("session-one");
    mvc.perform(get("/portal/api/profile")
            .cookie(new Cookie("LANG_SESSION", "session-one"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/portal/api/profile")
            .cookie(new Cookie("LANG_SESSION", "session-two"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isOk());
  }

  @Test
  void repeatedLogoutStaysSuccessfulAndStillExpiresCookies() throws Exception {
    Csrf csrf = csrf();
    String sessionId = "repeat-logout-session";
    doReturn(new PortalSessionRecord(sessionId, "upstream-session", 42L)).when(sessionStore).find(sessionId);
    doReturn(true).when(sessionStore).delete(sessionId);
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-session", 42L));
    Cookie[] cookies = {new Cookie("LANG_SESSION", sessionId), new Cookie("LANG_UID", "42")};

    MvcResult first = mvc.perform(authPost("/portal/api/auth/logout", csrf).cookie(cookies))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(expiredCookieHeaders(first.getResponse()))
        .anyMatch(header -> header.startsWith("LANG_SESSION="))
        .anyMatch(header -> header.startsWith("LANG_UID="));

    // 第二次退出：没有会话记录也必须成功，并继续清除 Cookie。
    doReturn(null).when(sessionStore).find(sessionId);
    MvcResult second = mvc.perform(authPost("/portal/api/auth/logout", csrf).cookie(cookies))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(expiredCookieHeaders(second.getResponse()))
        .anyMatch(header -> header.startsWith("LANG_SESSION="))
        .anyMatch(header -> header.startsWith("LANG_UID="));
  }

  @Test
  void upstreamLogoutFailureStillRevokesTheLocalSessionAndKeepsTheUpstreamErrorSemantics() throws Exception {
    Csrf csrf = csrf();
    String sessionId = "failing-logout-session";
    doReturn(new PortalSessionRecord(sessionId, "upstream-session", 42L)).when(sessionStore).find(sessionId);
    doReturn(USER).when(client).currentUser(new NewApiSession("upstream-session", 42L));
    doThrow(new com.lang.portal.base.exception.UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE))
        .when(client).logout(new NewApiSession("upstream-session", 42L));
    doReturn(true).when(sessionStore).delete(sessionId);
    Cookie[] cookies = {new Cookie("LANG_SESSION", sessionId), new Cookie("LANG_UID", "42")};

    MvcResult result = mvc.perform(authPost("/portal/api/auth/logout", csrf).cookie(cookies))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"))
        .andReturn();

    // 上游退出失败也必须撤销本地会话，并保留清除客户端 Cookie 的既有语义。
    verify(sessionStore).delete(sessionId);
    assertThat(expiredCookieHeaders(result.getResponse()))
        .anyMatch(header -> header.startsWith("LANG_SESSION="))
        .anyMatch(header -> header.startsWith("LANG_UID="));
    doReturn(null).when(sessionStore).find(sessionId);
    mvc.perform(get("/portal/api/profile").cookie(cookies))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sessionStorageFailureDoesNotSilentlyAuthenticateAnyone() throws Exception {
    doThrow(new IllegalStateException("storage unavailable")).when(sessionStore).find(any());

    // 存储故障不得放行，也不得当成"没有这条会话"（否则等于静默登出全部在线用户）。
    mvc.perform(get("/portal/api/profile")
            .cookie(new Cookie("LANG_SESSION", "whatever"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));

    verify(client, never()).currentUser(any());
  }

  @Test
  void logoutWithoutAnyServerSideSessionNeverCallsTheUpstream() throws Exception {
    Csrf csrf = csrf();
    doReturn(null).when(sessionStore).find("unknown-session");

    mvc.perform(authPost("/portal/api/auth/logout", csrf)
            .cookie(new Cookie("LANG_SESSION", "unknown-session"), new Cookie("LANG_UID", "42")))
        .andExpect(status().isOk());

    verify(client, never()).logout(any());
    verify(sessionStore, never()).delete(any());
  }

  private java.util.List<String> expiredCookieHeaders(MockHttpServletResponse response) {
    return response.getHeaders("Set-Cookie").stream()
        .filter(header -> header.contains("Max-Age=0"))
        .toList();
  }

  private String sessionCookie(MvcResult result) {
    return java.util.Arrays.stream(result.getResponse().getCookies())
        .filter(cookie -> "LANG_SESSION".equals(cookie.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("未签发 LANG_SESSION"))
        .getValue();
  }

  private Csrf csrf() throws Exception {
    MvcResult result = mvc.perform(get("/portal/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    return new Csrf(token);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(
      String path, Csrf csrf) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Origin", "http://portal.test")
        .header("X-XSRF-TOKEN", csrf.token())
        .cookie(new Cookie("XSRF-TOKEN", csrf.token()));
  }

  private record Csrf(String token) {}
}