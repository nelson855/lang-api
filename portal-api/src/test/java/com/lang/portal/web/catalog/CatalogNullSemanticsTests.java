package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogNullSemanticsTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void baselineFromListModelKeepsUnknownAsNull() {
    CatalogModel model =
        new CatalogModel(
            "gpt-example",
            null,
            "Example",
            "AVAILABLE",
            new CatalogPricing("TOKEN", "USD", "PER_MILLION_TOKENS", "2.5", "10.0", null));

    CatalogModelDetails details = CatalogModelDetails.baseline(model);

    assertThat(details.id()).isEqualTo("gpt-example");
    assertThat(details.provider()).isEqualTo("Example");
    assertThat(details.pricing()).isEqualTo(model.pricing());
    assertThat(details.displayName()).isNull();
    assertThat(details.contextWindowTokens()).isNull();
    assertThat(details.maxOutputTokens()).isNull();
    assertThat(details.inputModalities()).isNull();
    assertThat(details.outputModalities()).isNull();
    assertThat(details.capabilities()).isNotNull();
    assertThat(details.capabilities().toolCalling()).isNull();
    assertThat(details.capabilities().reasoning()).isNull();
    assertThat(details.capabilities().structuredOutput()).isNull();
    assertThat(details.capabilities().attachments()).isNull();
    assertThat(details.releaseDate()).isNull();
    assertThat(details.description()).isNull();
    assertThat(details.tags()).isNull();
    assertThat(details.sortOrder()).isNull();
    assertThat(details.enhancedPricing()).isNull();
  }

  @Test
  void jsonDistinguishesNullEmptyAndFalse() throws Exception {
    CatalogModelDetails unknown =
        new CatalogModelDetails(
            "m",
            null,
            null,
            "AVAILABLE",
            null,
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
    JsonNode unknownJson = mapper.valueToTree(unknown);
    assertThat(unknownJson.get("tags").isNull()).isTrue();
    assertThat(unknownJson.get("capabilities").get("toolCalling").isNull()).isTrue();

    CatalogModelDetails explicit =
        new CatalogModelDetails(
            "m",
            null,
            null,
            "AVAILABLE",
            null,
            null,
            null,
            List.of(),
            List.of(),
            new CatalogCapabilities(false, null, null, null),
            null,
            null,
            List.of(),
            null,
            null);
    JsonNode explicitJson = mapper.valueToTree(explicit);
    assertThat(explicitJson.get("tags").isArray()).isTrue();
    assertThat(explicitJson.get("tags").size()).isEqualTo(0);
    assertThat(explicitJson.get("inputModalities").size()).isEqualTo(0);
    assertThat(explicitJson.get("capabilities").get("toolCalling").asBoolean()).isFalse();
  }
}
