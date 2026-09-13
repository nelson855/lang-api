package com.lang.portal.web.apikey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.SensitiveSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyCrossUserTests {

  @Autowired private MockMvc mvc;

  @MockBean private NewApiAuthenticationClient authClient;
  @MockBean private NewApiTokenClient tokenClient;

  private static final NewApiSession SESSION_A = new NewApiSession("user-a-session", 1L);

  @BeforeEach
  void authenticatedAsUserA() {
    when(authClient.currentUser(SESSION_A))
        .thenReturn(new NewApiUserProfile(1L, "user-a", "User A", "a@example.test"));
  }

  private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
    return builder
        .cookie(new Cookie("LANG_SESSION", "user-a-session"), new Cookie("LANG_UID", "1"))
        .cookie(new Cookie("XSRF-TOKEN", "csrf-token"))
        .header("X-XSRF-TOKEN", "csrf-token")
        .header("Origin", "http://portal.test");
  }

  private static NewApiToken ownToken() {
    return new NewApiToken(
        7L, 1L, "fN95**********CMHQ", 1, "mine", 1789180807L, 1789180807L,
        -1L, 100L, false, false, "", "", 0L, "", false);
  }

  @Test
  void otherUsersResourcesLookMissingAcrossOperations() throws Exception {
    when(tokenClient.getToken(any(), eq(999L))).thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));
    doThrowNotFoundUpdate();
    when(tokenClient.revealToken(any(), eq(999L))).thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));

    mvc.perform(authed(get("/portal/api/api-keys/999")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    mvc.perform(authed(put("/portal/api/api-keys/999")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(authed(put("/portal/api/api-keys/999/status")
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(authed(delete("/portal/api/api-keys/999")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(authed(post("/portal/api/api-keys/999/reveal")))
        .andExpect(status().isNotFound())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-"))));
  }

  @Test
  void ownResourceFlowsEndToEnd() throws Exception {
    when(tokenClient.getToken(any(), eq(7L))).thenReturn(ownToken());
    when(tokenClient.revealToken(any(), eq(7L))).thenReturn(SensitiveSecret.of("sk-abc"));

    mvc.perform(authed(get("/portal/api/api-keys/7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("mine"))
        .andExpect(jsonPath("$.data.maskedKey").value("sk-fN95**********CMHQ"));
    mvc.perform(authed(post("/portal/api/api-keys/7/reveal")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.secret").value("sk-abc"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  private void doThrowNotFoundUpdate() {
    org.mockito.Mockito.doThrow(new PortalException(PortalErrorCode.NOT_FOUND))
        .when(tokenClient).updateToken(any(), eq(999L), any());
    org.mockito.Mockito.doThrow(new PortalException(PortalErrorCode.NOT_FOUND))
        .when(tokenClient).updateStatus(any(), eq(999L), anyBoolean());
    org.mockito.Mockito.doThrow(new PortalException(PortalErrorCode.NOT_FOUND))
        .when(tokenClient).deleteToken(any(), eq(999L));
  }

  private static Boolean anyBoolean() {
    return org.mockito.ArgumentMatchers.anyBoolean();
  }

  private static Long anyLong() {
    return org.mockito.ArgumentMatchers.anyLong();
  }
}
