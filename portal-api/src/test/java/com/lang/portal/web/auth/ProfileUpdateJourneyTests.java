package com.lang.portal.web.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.profile.NewApiProfileUpdateClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileUpdateJourneyTests {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private NewApiAuthenticationClient authClient;
  @MockitoBean private NewApiProfileUpdateClient profileUpdateClient;

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private String csrf() throws Exception {
    MvcResult result =
        mvc.perform(get("/portal/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
  }

  private Cookie[] cookies() {
    return new Cookie[] {
      new Cookie("LANG_SESSION", SESSION.value()), new Cookie("LANG_UID", "42"),
    };
  }

  @Test
  void updateSuccessReturnsRereadProfileAndKeepsSession() throws Exception {
    String token = csrf();
    when(authClient.currentUser(eq(SESSION)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"))
        .thenReturn(new NewApiUserProfile(42L, "newname", "New Name", "ordinary@example.test"));
    when(profileUpdateClient.update(eq(SESSION), any()))
        .thenReturn(new NewApiUserProfile(42L, "newname", "New Name", "ordinary@example.test"));

    mvc.perform(
            put("/portal/api/profile")
                .cookie(cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "http://portal.test")
                .header("X-XSRF-TOKEN", token)
                .cookie(new Cookie("XSRF-TOKEN", token))
                .content("{\"username\":\"newname\",\"displayName\":\"New Name\",\"currentPassword\":\"correct-123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("newname"))
        .andExpect(jsonPath("$.data.displayName").value("New Name"))
        .andExpect(header().doesNotExist("Set-Cookie"));

    mvc.perform(get("/portal/api/profile").cookie(cookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("newname"));
  }

  @Test
  void updateFailureOmitsPasswordsFromResponse() throws Exception {
    String token = csrf();
    when(authClient.currentUser(eq(SESSION)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    when(profileUpdateClient.update(eq(SESSION), any()))
        .thenThrow(new PortalException(PortalErrorCode.INVALID_ARGUMENT));

    MvcResult result =
        mvc.perform(
                put("/portal/api/profile")
                    .cookie(cookies())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Origin", "http://portal.test")
                    .header("X-XSRF-TOKEN", token)
                    .cookie(new Cookie("XSRF-TOKEN", token))
                    .content(
                        "{\"username\":\"newname\",\"displayName\":\"New Name\","
                            + "\"currentPassword\":\"correct-123\",\"newPassword\":\"brand-new-123\","
                            + "\"confirmPassword\":\"brand-new-123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
            .andReturn();

    assertThatBodyOmitsPasswords(result);
  }

  @Test
  void oversizedBodyRejectedBeforeUpstream() throws Exception {
    String big = "a".repeat(1_048_577);

    mvc.perform(
            put("/portal/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Length", String.valueOf(big.length()))
                .content("{\"v\":\"" + big + "\"}"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    verifyNoInteractions(profileUpdateClient);
  }

  @Test
  void wrongMethodRejectedBeforeUpstream() throws Exception {
    mvc.perform(
            post("/portal/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newname\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(profileUpdateClient);
  }

  @Test
  void unknownSubpathIsNotFoundWithValidCsrf() throws Exception {
    String token = csrf();
    when(authClient.currentUser(eq(SESSION)))
        .thenReturn(new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));

    mvc.perform(
            put("/portal/api/profile/extra")
                .cookie(cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "http://portal.test")
                .header("X-XSRF-TOKEN", token)
                .cookie(new Cookie("XSRF-TOKEN", token))
                .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    verifyNoInteractions(profileUpdateClient);
  }

  private void assertThatBodyOmitsPasswords(MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("correct-123", "brand-new-123");
    mvc.perform(get("/portal/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("correct-123"))));
  }
}
