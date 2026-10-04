package com.lang.portal.web.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogSpecialRouteTests {

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
      Mockito.when(stub.findDetails(Mockito.argThat(id -> id != null && !id.equals("a-model"))))
          .thenReturn(Optional.empty());
      Mockito.when(stub.snapshot())
          .thenReturn(
              new CatalogSnapshot(
                  "v1",
                  new CatalogData("v1", List.of(model)),
                  Map.of("a-model", details),
                  List.of()));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;

  @Test
  void extraPathSegmentIsRejected() throws Exception {
    String ref = ModelRef.encode("a-model");
    mvc.perform(get("/portal/api/models/{ref}/extra", ref))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void dotSegmentsDoNotPenetrate() throws Exception {
    MvcResult result = mvc.perform(get("/portal/api/models/./..")).andReturn();
    assertThat4xx(result);
  }

  @Test
  void doubleEncodedRefDoesNotPenetrate() throws Exception {
    String ref = ModelRef.encode("a-model");
    String doubleEncoded = ModelRef.encode(ref);
    mvc.perform(get("/portal/api/models/{ref}", doubleEncoded))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void controlCharIdIsRejected() throws Exception {
    String ref =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("badid".getBytes(StandardCharsets.UTF_8));
    mvc.perform(get("/portal/api/models/{ref}", ref))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void internalConfigNameDoesNotPenetrate() throws Exception {
    String ref = ModelRef.encode("lang.portal.catalog.quota-per-usd");
    MvcResult result =
        mvc.perform(get("/portal/api/models/{ref}", ref)).andReturn();
    int status = result.getResponse().getStatus();
    org.assertj.core.api.Assertions.assertThat(status).isIn(400, 404);
    String body = result.getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("500000");
  }

  private void assertThat4xx(MvcResult result) throws Exception {
    int status = result.getResponse().getStatus();
    org.assertj.core.api.Assertions.assertThat(status).isIn(400, 404, 405);
    String body = result.getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("a-model");
  }
}
