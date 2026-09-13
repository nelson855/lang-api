package com.lang.portal.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthOptionsControllerTests {

  @Autowired private MockMvc mvc;

  @Test
  void anonymousClientCanReadFixedAuthenticationOptions() throws Exception {
    mvc.perform(get("/portal/api/auth/options"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").doesNotExist())
        .andExpect(jsonPath("$.data.registrationEnabled").value(true))
        .andExpect(jsonPath("$.data.emailVerificationEnabled").value(false))
        .andExpect(jsonPath("$.data.captchaEnabled").value(false));
  }

  @Test
  void anonymousClientCanBootstrapCsrfToken() throws Exception {
    mvc.perform(get("/portal/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("XSRF-TOKEN=")))
        .andExpect(jsonPath("$.data.token").isNotEmpty());
  }

  @Test
  void registerRejectsMissingCsrfBeforeCallingUpstream() throws Exception {
    mvc.perform(post("/portal/api/auth/register")
            .contentType("application/json")
            .content("{\"username\":\"ordinary\",\"password\":\"correct-horse\",\"confirmPassword\":\"correct-horse\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
  }

  @Test
  void registerValidatesFieldsAfterCsrfAndBeforeAnyRegistrationAttempt() throws Exception {
    mvc.perform(post("/portal/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Origin", "http://portal.test")
            .header("X-XSRF-TOKEN", "csrf-token")
            .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token"))
            .content("{\"username\":\"x\",\"password\":\"short\",\"confirmPassword\":\"different\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void everyAuthenticationMutationRejectsMissingCsrfBeforeReachingItsEndpoint() throws Exception {
    for (String path : java.util.List.of(
        "/portal/api/auth/login",
        "/portal/api/auth/refresh",
        "/portal/api/auth/logout")) {
      mvc.perform(post(path))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    }
  }
}
