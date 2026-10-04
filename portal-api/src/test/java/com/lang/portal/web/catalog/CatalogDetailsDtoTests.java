package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDetailsDtoTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void fullDetailsExposeFrozenFieldNamesAndTypes() throws Exception {
    CatalogModelDetails details =
        new CatalogModelDetails(
            "gpt-example",
            "示例模型",
            "Example",
            "AVAILABLE",
            new CatalogPricing("TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null),
            128000,
            8192,
            List.of(ModelModality.TEXT, ModelModality.IMAGE),
            List.of(ModelModality.TEXT),
            new CatalogCapabilities(true, null, false, null),
            "2026-09-01",
            "示例描述",
            List.of("fast", "chat"),
            10,
            List.of(
                new EnhancedPricingItem(
                    EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "0.001")));

    JsonNode json = mapper.valueToTree(details);
    assertThat(json.get("id").asText()).isEqualTo("gpt-example");
    assertThat(json.get("displayName").asText()).isEqualTo("示例模型");
    assertThat(json.get("provider").asText()).isEqualTo("Example");
    assertThat(json.get("availability").asText()).isEqualTo("AVAILABLE");
    assertThat(json.get("pricing").get("mode").asText()).isEqualTo("TOKEN");
    assertThat(json.get("contextWindowTokens").asInt()).isEqualTo(128000);
    assertThat(json.get("maxOutputTokens").asInt()).isEqualTo(8192);
    assertThat(json.get("inputModalities").toString()).contains("TEXT", "IMAGE");
    assertThat(json.get("outputModalities").get(0).asText()).isEqualTo("TEXT");
    assertThat(json.get("capabilities").get("toolCalling").asBoolean()).isTrue();
    assertThat(json.get("capabilities").get("structuredOutput").asBoolean()).isFalse();
    assertThat(json.get("releaseDate").asText()).isEqualTo("2026-09-01");
    assertThat(json.get("description").asText()).isEqualTo("示例描述");
    assertThat(json.get("tags").get(0).asText()).isEqualTo("fast");
    assertThat(json.get("sortOrder").asInt()).isEqualTo(10);
    assertThat(json.get("enhancedPricing").get(0).get("type").asText()).isEqualTo("SEARCH");
    assertThat(json.get("enhancedPricing").get(0).get("currency").asText()).isEqualTo("USD");
    assertThat(json.get("enhancedPricing").get(0).get("unit").asText()).isEqualTo("PER_REQUEST");
    assertThat(json.get("enhancedPricing").get(0).get("price").asText()).isEqualTo("0.001");
  }

  @Test
  void baselineNullsKeepStructure() throws Exception {
    CatalogModelDetails details =
        new CatalogModelDetails(
            "gpt-example",
            null,
            "Example",
            "AVAILABLE",
            new CatalogPricing("TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null),
            null,
            null,
            null,
            null,
            new CatalogCapabilities(null, null, null, null),
            null,
            null,
            null,
            null,
            null);

    JsonNode json = mapper.valueToTree(details);
    assertThat(json.has("contextWindowTokens")).isTrue();
    assertThat(json.get("contextWindowTokens").isNull()).isTrue();
    assertThat(json.get("maxOutputTokens").isNull()).isTrue();
    assertThat(json.get("inputModalities").isNull()).isTrue();
    assertThat(json.get("outputModalities").isNull()).isTrue();
    assertThat(json.has("capabilities")).isTrue();
    assertThat(json.get("capabilities").get("toolCalling").isNull()).isTrue();
    assertThat(json.get("capabilities").get("reasoning").isNull()).isTrue();
    assertThat(json.get("capabilities").get("structuredOutput").isNull()).isTrue();
    assertThat(json.get("capabilities").get("attachments").isNull()).isTrue();
    assertThat(json.get("releaseDate").isNull()).isTrue();
    assertThat(json.get("description").isNull()).isTrue();
    assertThat(json.get("tags").isNull()).isTrue();
    assertThat(json.get("sortOrder").isNull()).isTrue();
    assertThat(json.get("enhancedPricing").isNull()).isTrue();
  }

  @Test
  void allowedEnumValuesAreFrozen() {
    assertThat(ModelModality.values())
        .containsExactlyInAnyOrder(
            ModelModality.TEXT,
            ModelModality.IMAGE,
            ModelModality.AUDIO,
            ModelModality.VIDEO,
            ModelModality.FILE);
    assertThat(EnhancedPriceType.values())
        .containsExactlyInAnyOrder(
            EnhancedPriceType.CACHE_INPUT,
            EnhancedPriceType.CACHE_OUTPUT,
            EnhancedPriceType.IMAGE_INPUT,
            EnhancedPriceType.IMAGE_OUTPUT,
            EnhancedPriceType.AUDIO_INPUT,
            EnhancedPriceType.AUDIO_OUTPUT,
            EnhancedPriceType.VIDEO,
            EnhancedPriceType.SEARCH);
  }
}
