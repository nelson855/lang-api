package com.lang.portal.web.catalog;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
class CatalogDetailControllerTests {

  @TestConfiguration
  static class Stub {
    @Bean
    @Primary
    CatalogService catalogService() {
      CatalogService stub = Mockito.mock(CatalogService.class);
      CatalogModel model =
          new CatalogModel(
              "a-model",
              null,
              "Example",
              "AVAILABLE",
              new CatalogPricing("TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null));
      CatalogModelDetails details = CatalogModelDetails.baseline(model);
      when(stub.findDetails("a-model")).thenReturn(Optional.of(details));
      when(stub.findDetails("missing-model")).thenReturn(Optional.empty());
      when(stub.snapshot())
          .thenReturn(
              new CatalogSnapshot(
                  "v1",
                  new CatalogData("v1", List.of(model)),
                  java.util.Map.of("a-model", details),
                  List.of()));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private CatalogService catalogService;

  @Test
  void anonymousSuccessWithNoStore() throws Exception {
    String ref = ModelRef.encode("a-model");
    mvc.perform(get("/portal/api/models/{ref}", ref))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.data.pricingVersion").value("v1"))
        .andExpect(jsonPath("$.data.model.id").value("a-model"))
        .andExpect(jsonPath("$.data.model.provider").value("Example"))
        .andExpect(jsonPath("$.data.model.capabilities.toolCalling").isEmpty());
  }

  @Test
  void illegalRefReturns400WithoutTouchingSnapshot() throws Exception {
    Mockito.clearInvocations(catalogService);
    mvc.perform(get("/portal/api/models/{ref}", "ab*cd"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    verify(catalogService, never()).findDetails(anyString());
  }

  @Test
  void paddedRefReturns400() throws Exception {
    mvc.perform(get("/portal/api/models/{ref}", "YWJj="))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void missingModelReturns404() throws Exception {
    String ref = ModelRef.encode("missing-model");
    mvc.perform(get("/portal/api/models/{ref}", ref))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void unknownQueryParamReturns400() throws Exception {
    String ref = ModelRef.encode("a-model");
    mvc.perform(get("/portal/api/models/{ref}?foo=bar", ref))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void wrongMethodReturns405() throws Exception {
    String ref = ModelRef.encode("a-model");
    mvc.perform(post("/portal/api/models/{ref}", ref))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void specialCharIdRoundTrip() throws Exception {
    String id = "vendor/model%name with 空格";
    CatalogModel model =
        new CatalogModel(
            id,
            null,
            "Example",
            "AVAILABLE",
            new CatalogPricing("REQUEST", "USD", "PER_REQUEST", null, null, "0.003"));
    CatalogModelDetails details = CatalogModelDetails.baseline(model);
    when(catalogService.findDetails(id)).thenReturn(Optional.of(details));
    String ref = ModelRef.encode(id);
    mvc.perform(get("/portal/api/models/{ref}", ref))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.model.id").value(id));
  }
}
