package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Set;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogListRegressionTests {

  @TestConfiguration
  static class Stub {
    @Bean
    @Primary
    CatalogService catalogService() {
      CatalogService stub = Mockito.mock(CatalogService.class);
      Mockito.when(stub.current())
          .thenReturn(
              new CatalogData(
                  "v1",
                  List.of(
                      new CatalogModel(
                          "b-model",
                          null,
                          "Example",
                          "AVAILABLE",
                          new CatalogPricing(
                              "TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null)),
                      new CatalogModel(
                          "a-model",
                          null,
                          null,
                          "AVAILABLE",
                          new CatalogPricing(
                              "REQUEST", "USD", "PER_REQUEST", null, null, "0.003")))));
      return stub;
    }
  }

  @Autowired private MockMvc mvc;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void listKeepsExactShapeAndOrder() throws Exception {
    String body =
        mvc.perform(get("/portal/api/models")).andReturn().getResponse().getContentAsString();
    JsonNode root = mapper.readTree(body);
    assertThat(root.get("data").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("pricingVersion", "models");
    JsonNode models = root.get("data").get("models");
    assertThat(models.get(0).get("id").asText()).isEqualTo("b-model");
    for (JsonNode m : models) {
      assertThat(fieldNames(m))
          .containsExactlyInAnyOrder("id", "displayName", "provider", "availability", "pricing");
      JsonNode pricing = m.get("pricing");
      if (!pricing.isNull()) {
        assertThat(fieldNames(pricing))
            .containsExactlyInAnyOrder("mode", "currency", "unit", "input", "output", "request");
      }
    }
    String lower = body.toLowerCase();
    for (String banned :
        new String[] {"contextwindow", "capabilities", "enhancedpricing", "providers"}) {
      assertThat(lower).doesNotContain(banned);
    }
  }

  private static Set<String> fieldNames(JsonNode node) {
    java.util.HashSet<String> names = new java.util.HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
