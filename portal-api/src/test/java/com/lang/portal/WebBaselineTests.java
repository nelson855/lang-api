package com.lang.portal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebBaselineTests {

  @Autowired
  private MockMvc mvc;

  @Test
  void rootServesReactEntry() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("index.html"));
    mvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"root\"")));
  }

  @Test
  void deepRouteFallsBackToEntry() throws Exception {
    mvc.perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"root\"")));
  }

  @Test
  void unknownPortalApiReturnsJson404() throws Exception {
    mvc.perform(get("/portal/api/test"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
        .andExpect(content().string(not(containsString("id=\"root\""))))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void unknownActuatorPathDoesNotFallBack() throws Exception {
    mvc.perform(get("/actuator/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("id=\"root\""))));
  }

  @Test
  void missingStaticFileDoesNotFallBack() throws Exception {
    mvc.perform(get("/assets/missing.js"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("id=\"root\""))));
  }

  @Test
  void healthIsUpWithoutSensitiveDetails() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(content().string(not(containsString("secret"))))
        .andExpect(content().string(not(containsString("password"))));
  }

  @Test
  void infoExposesAppNameAndMavenVersion() throws Exception {
    mvc.perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("lang-api")))
        .andExpect(content().string(containsString("0.1.0-SNAPSHOT")))
        .andExpect(content().string(not(containsString("secret"))));
  }
}
