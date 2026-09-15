package com.lang.portal.web;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicConfigContractTests {

  @Autowired private MockMvc mvc;

  @Test
  void returnsWrappedPublicFieldsAnonymously() throws Exception {
    mvc.perform(get("/portal/api/public-config"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.data.siteName").isNotEmpty())
        .andExpect(jsonPath("$.data.publicationMode").value("PREVIEW"))
        .andExpect(jsonPath("$.data.siteUrl").value("http://portal.test"))
        .andExpect(jsonPath("$.data.supportUrl").value("mailto:test@portal.test"))
        .andExpect(jsonPath("$.data.supportedRegions[0]").value("CN"))
        .andExpect(jsonPath("$.data.enabledLocales").isArray())
        .andExpect(jsonPath("$.data.apiBaseUrls").isArray());
  }

  @Test
  void wrongMethodReturns405() throws Exception {
    mvc.perform(post("/portal/api/public-config"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void doesNotLeakUpstreamFields() throws Exception {
    String body = mvc.perform(get("/portal/api/public-config")).andReturn().getResponse().getContentAsString();
    for (String banned : new String[] {"version", "serverAddress", "oauth", "ratio", "template", "internal"}) {
      org.assertj.core.api.Assertions.assertThat(body.toLowerCase()).doesNotContain(banned);
    }
  }
}
