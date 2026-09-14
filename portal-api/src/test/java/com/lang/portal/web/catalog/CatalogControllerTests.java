package com.lang.portal.web.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogControllerTests {

  @TestConfiguration
  static class Stub {
    @Bean
    @Primary
    CatalogService catalogService() {
      CatalogService stub = Mockito.mock(CatalogService.class);
      Mockito.when(stub.current()).thenReturn(new CatalogData("v1",
          List.of(new CatalogModel("a-model", null, "Example", "AVAILABLE",
              new CatalogPricing("TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null)))));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;

  @Test
  void anonymousSuccessWithNoStore() throws Exception {
    mvc.perform(get("/portal/api/models"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.data.models[0].id").value("a-model"))
        .andExpect(jsonPath("$.data.models[0].pricing.mode").value("TOKEN"));
  }

  @Test
  void wrongMethodReturns405() throws Exception {
    mvc.perform(post("/portal/api/models"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void doesNotLeakInternalFields() throws Exception {
    String body = mvc.perform(get("/portal/api/models")).andReturn().getResponse().getContentAsString();
    String lower = body.toLowerCase();
    for (String banned : new String[] {"group_ratio", "usable_group", "enable_groups", "channel", "model_ratio", "new-api-user"}) {
      org.assertj.core.api.Assertions.assertThat(lower).doesNotContain(banned);
    }
  }
}
