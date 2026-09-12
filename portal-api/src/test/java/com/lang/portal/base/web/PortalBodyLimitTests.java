package com.lang.portal.base.web;

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
class PortalBodyLimitTests {

  @Autowired private MockMvc mvc;

  @Test
  void declaredLengthOverLimitReturns413() throws Exception {
    String big = "a".repeat(1_048_577);
    mvc.perform(post("/portal/api/public-config")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Content-Length", String.valueOf(big.length()))
            .content("{\"v\":\"" + big + "\"}"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
  }
}
