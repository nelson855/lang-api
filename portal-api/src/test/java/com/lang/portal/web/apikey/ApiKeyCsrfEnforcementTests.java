package com.lang.portal.web.apikey;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyCsrfEnforcementTests {

  @Autowired private MockMvc mvc;

  @MockBean private NewApiTokenClient tokenClient;

  private static String body() {
    return "{\"name\":\"key-1\",\"unlimited\":false,\"remaining\":1}";
  }

  @Test
  void createWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(post("/portal/api/api-keys").contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(tokenClient);
  }

  @Test
  void updateWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(put("/portal/api/api-keys/7").contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(tokenClient);
  }

  @Test
  void statusWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(put("/portal/api/api-keys/7/status")
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(tokenClient);
  }

  @Test
  void deleteWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(delete("/portal/api/api-keys/7"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(tokenClient);
  }

  @Test
  void revealWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(post("/portal/api/api-keys/7/reveal"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(tokenClient);
  }
}
