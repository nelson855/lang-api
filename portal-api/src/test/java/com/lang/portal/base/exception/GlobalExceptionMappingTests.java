package com.lang.portal.base.exception;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionMappingTests {

  @Autowired private MockMvc mvc;

  @Test
  void validationFailureIsSafe() throws Exception {
    mvc.perform(get("/portal/api/test-validation").param("name", "").param("age", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
    String body = mvc.perform(post("/portal/api/test-validation")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
        .andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("ConstraintViolation")
        .doesNotContain("java.lang")
        .doesNotContain("stackTrace");
  }

  @Test
  void unknownPortalReturnsUnified404() throws Exception {
    mvc.perform(get("/portal/api/definitely-unknown"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void knownPathWrongMethodReturns405() throws Exception {
    mvc.perform(post("/portal/api/public-config").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void authenticationErrorsUseStableHttpContracts() throws Exception {
    mvc.perform(get("/portal/api/test-validation/errors/INVALID_CREDENTIALS"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    mvc.perform(get("/portal/api/test-validation/errors/REGISTRATION_DISABLED"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("REGISTRATION_DISABLED"));
    mvc.perform(get("/portal/api/test-validation/errors/CSRF_REJECTED"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    mvc.perform(get("/portal/api/test-validation/errors/RATE_LIMITED"))
        .andExpect(status().isTooManyRequests())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
            .string("Retry-After", "60"))
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
  }
}
