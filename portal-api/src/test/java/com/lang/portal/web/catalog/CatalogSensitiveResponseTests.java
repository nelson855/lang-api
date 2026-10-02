package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
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
class CatalogSensitiveResponseTests {

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
      Mockito.when(stub.findDetails("a-model")).thenReturn(Optional.of(details));
      Mockito.when(stub.snapshot())
          .thenReturn(
              new CatalogSnapshot(
                  "v1",
                  new CatalogData("v1", List.of(model)),
                  Map.of("a-model", details),
                  List.of(new ModelProviderOption("Example", "Example", 1))));
      Mockito.when(stub.providers())
          .thenReturn(List.of(new ModelProviderOption("Example", "Example", 1)));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;

  @Test
  void detailAndProvidersDoNotLeakInternals() throws Exception {
    String detail =
        mvc.perform(get("/portal/api/models/{ref}", ModelRef.encode("a-model")))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .toLowerCase();
    String providers =
        mvc.perform(get("/portal/api/model-providers"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .toLowerCase();
    for (String banned :
        new String[] {
          "vendor_id",
          "vendorid",
          "icon",
          "model_ratio",
          "completion_ratio",
          "group_ratio",
          "enable_groups",
          "usable_group",
          "channel",
          "owner",
          "endpoint",
          "new-api-user",
          "base_url",
          "quota_type"
        }) {
      assertThat(detail).doesNotContain(banned);
      assertThat(providers).doesNotContain(banned);
    }
  }
}
