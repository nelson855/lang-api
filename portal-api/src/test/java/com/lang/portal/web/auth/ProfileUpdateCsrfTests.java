package com.lang.portal.web.auth;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lang.portal.upstream.newapi.profile.NewApiProfileUpdateClient;
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
class ProfileUpdateCsrfTests {

  @Autowired private MockMvc mvc;

  @MockBean private NewApiProfileUpdateClient profileUpdateClient;

  @Test
  void updateWithoutCsrfRejectedBeforeUpstream() throws Exception {
    mvc.perform(
            put("/portal/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newname\",\"displayName\":\"New Name\",\"currentPassword\":\"x\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("CSRF_REJECTED"));
    verifyNoInteractions(profileUpdateClient);
  }
}
