package com.lang.portal.web.apikey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class ApiKeyWebContractTests {

  @Autowired
  private MockMvc mvc;

  @Test
  void undeclaredMethodOnRevealPathReturns405() throws Exception {
    mvc.perform(get("/portal/api/api-keys/7/reveal"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void unknownSubPathReturnsUnifiedNotFound() throws Exception {
    mvc.perform(get("/portal/api/api-keys/abc/extra"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }
}
