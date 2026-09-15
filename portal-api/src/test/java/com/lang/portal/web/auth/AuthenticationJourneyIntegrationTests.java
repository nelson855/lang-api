package com.lang.portal.web.auth;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationJourneyIntegrationTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final NewApiUserProfile USER = new NewApiUserProfile(
      42L, "ordinary", "Ordinary User", "ordinary@example.test");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PortalCommonProperties properties;
  @Autowired private PublicationProperties publication;
  @MockitoBean private NewApiAuthenticationClient client;
  @MockitoBean private com.lang.portal.web.legal.LegalContentService legalContent;

  @AfterEach
  void resetRegistration() {
    properties.auth().registration().setEnabled(true);
    publication.setMode(PublicationMode.PREVIEW);
  }

  private void openRegistrationGate() {
    publication.setMode(PublicationMode.PUBLIC);
    when(legalContent.document(com.lang.portal.web.legal.LegalContentType.TERMS))
        .thenReturn(new com.lang.portal.web.legal.LegalContentDocument(
            com.lang.portal.web.legal.LegalContentType.TERMS, "用户协议", "<p>safe</p>", "zh-CN"));
    when(legalContent.document(com.lang.portal.web.legal.LegalContentType.PRIVACY))
        .thenReturn(new com.lang.portal.web.legal.LegalContentDocument(
            com.lang.portal.web.legal.LegalContentType.PRIVACY, "隐私政策", "<p>safe</p>", "zh-CN"));
  }

  @Test
  void registrationLoginProfileRefreshLogoutAndOldCookieExpiryFormOneJourney() throws Exception {
    Csrf csrf = csrf();
    openRegistrationGate();
    mvc.perform(authPost("/portal/api/auth/register", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\",\"confirmPassword\":\"correct-horse\"}"))
        .andExpect(status().isOk());

    doReturn(new NewApiLoginResult(SESSION, USER))
        .when(client).login(new com.lang.portal.upstream.newapi.auth.NewApiCredentials("ordinary", "correct-horse"));
    mvc.perform(authPost("/portal/api/auth/login", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("ordinary"));

    doReturn(USER).doReturn(USER).doThrow(new PortalException(PortalErrorCode.UNAUTHENTICATED))
        .when(client).currentUser(SESSION);
    Cookie[] sessionCookies = {new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42")};
    mvc.perform(get("/portal/api/profile").cookie(sessionCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").doesNotExist());
    mvc.perform(authPost("/portal/api/auth/refresh", csrf).cookie(sessionCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("ordinary@example.test"));
    mvc.perform(authPost("/portal/api/auth/logout", csrf).cookie(sessionCookies))
        .andExpect(status().isOk());
    verify(client).logout(SESSION);
    mvc.perform(get("/portal/api/profile").cookie(sessionCookies))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void closedRegistrationAndInvalidCredentialsRemainStableWithoutLeakingUpstreamDetails() throws Exception {
    Csrf csrf = csrf();
    properties.auth().registration().setEnabled(false);
    mvc.perform(authPost("/portal/api/auth/register", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\",\"confirmPassword\":\"correct-horse\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("REGISTRATION_DISABLED"));

    doThrow(new PortalException(PortalErrorCode.INVALID_CREDENTIALS))
        .when(client).login(new com.lang.portal.upstream.newapi.auth.NewApiCredentials("ordinary", "incorrect-password"));
    mvc.perform(authPost("/portal/api/auth/login", csrf)
            .content("{\"username\":\"ordinary\",\"password\":\"incorrect-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  private Csrf csrf() throws Exception {
    MvcResult result = mvc.perform(get("/portal/api/auth/csrf"))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    return new Csrf(token);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(String path, Csrf csrf) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Origin", "http://portal.test")
        .header("X-XSRF-TOKEN", csrf.token())
        .cookie(new Cookie("XSRF-TOKEN", csrf.token()));
  }

  private record Csrf(String token) {}
}
