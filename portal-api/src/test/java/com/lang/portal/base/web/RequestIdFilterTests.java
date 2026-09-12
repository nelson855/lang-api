package com.lang.portal.base.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.lang.portal.base.response.RequestIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTests {

  @Autowired private MockMvc mvc;

  @Test
  void reusesValidClientId() throws Exception {
    mvc.perform(get("/portal/api/public-config").header(RequestIds.HEADER, "abc_123-XYZ:9"))
        .andExpect(header().string(RequestIds.HEADER, "abc_123-XYZ:9"))
        .andExpect(jsonPath("$.requestId").value("abc_123-XYZ:9"));
  }

  @Test
  void replacesMissingAndIllegalValues() throws Exception {
    MvcResult missing =
        mvc.perform(get("/portal/api/public-config"))
            .andExpect(header().exists(RequestIds.HEADER))
            .andReturn();
    String generated = missing.getResponse().getHeader(RequestIds.HEADER);
    assertThat(generated).startsWith("req_");

    mvc.perform(get("/portal/api/public-config").header(RequestIds.HEADER, "bad value\ninject"))
        .andExpect(header().string(RequestIds.HEADER, org.hamcrest.Matchers.not("bad value\ninject")));
  }

  @Test
  void bodyAndHeaderShareSameId() throws Exception {
    MvcResult result =
        mvc.perform(get("/portal/api/public-config").header(RequestIds.HEADER, "valid-1234"))
            .andReturn();
    String headerId = result.getResponse().getHeader(RequestIds.HEADER);
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains(headerId);
  }
}
