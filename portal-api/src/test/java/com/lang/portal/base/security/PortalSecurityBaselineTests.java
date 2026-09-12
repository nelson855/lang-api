package com.lang.portal.base.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalSecurityBaselineTests {

  @Autowired private MockMvc mvc;

  @Test
  void anonymousCanAccessPublicConfig() throws Exception {
    mvc.perform(get("/portal/api/public-config")).andExpect(status().isOk());
  }

  @Test
  void unexposedActuatorIsDenied() throws Exception {
    mvc.perform(get("/actuator/env"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void errorResponsesKeepSecurityHeaders() throws Exception {
    mvc.perform(get("/portal/api/unknown-for-security"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void untrustedForwardedProtoDoesNotTriggerHsts() throws Exception {
    mvc.perform(get("/portal/api/public-config").header("X-Forwarded-Proto", "https"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"));
  }

  @Test
  void anonymousProtectedReturns401() throws Exception {
    mvc.perform(get("/portal/api/test-protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  @WithMockUser
  void forbiddenReturns403() throws Exception {
    mvc.perform(get("/portal/api/test-protected/admin"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
