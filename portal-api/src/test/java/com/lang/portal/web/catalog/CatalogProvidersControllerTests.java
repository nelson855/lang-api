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
class CatalogProvidersControllerTests {

  @TestConfiguration
  static class Stub {
    @Bean
    @Primary
    CatalogService catalogService() {
      CatalogService stub = Mockito.mock(CatalogService.class);
      Mockito.when(stub.snapshot())
          .thenReturn(
              new CatalogSnapshot(
                  "v1",
                  new CatalogData("v1", List.of()),
                  java.util.Map.of(),
                  List.of(
                      new ModelProviderOption("DeepSeek", "DeepSeek", 2),
                      new ModelProviderOption("OpenAI", "OpenAI", 1))));
      Mockito.when(stub.providers())
          .thenReturn(
              List.of(
                  new ModelProviderOption("DeepSeek", "DeepSeek", 2),
                  new ModelProviderOption("OpenAI", "OpenAI", 1)));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private CatalogService catalogService;

  @Test
  void anonymousSuccessWithNoStore() throws Exception {
    mvc.perform(get("/portal/api/model-providers"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.data.pricingVersion").value("v1"))
        .andExpect(jsonPath("$.data.providers[0].value").value("DeepSeek"))
        .andExpect(jsonPath("$.data.providers[0].label").value("DeepSeek"))
        .andExpect(jsonPath("$.data.providers[0].modelCount").value(2))
        .andExpect(jsonPath("$.data.providers[1].value").value("OpenAI"));
  }

  @Test
  void unknownQueryParamReturns400() throws Exception {
    mvc.perform(get("/portal/api/model-providers?foo=bar"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void wrongMethodReturns405() throws Exception {
    mvc.perform(post("/portal/api/model-providers"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void emptyProvidersReturnsEmptyArray() throws Exception {
    Mockito.when(catalogService.snapshot())
        .thenReturn(new CatalogSnapshot("v1", new CatalogData("v1", List.of()),
            java.util.Map.of(), List.of()));
    mvc.perform(get("/portal/api/model-providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.providers").isArray())
        .andExpect(jsonPath("$.data.providers").isEmpty());
  }
}
