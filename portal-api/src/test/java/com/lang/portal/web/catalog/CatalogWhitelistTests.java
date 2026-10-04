package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogWhitelistTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void modelNameOrProviderDoesNotInferCapabilities() {
    var entries =
        List.of(
            new NewApiPricingEntry(
                "gpt-4-vision-tool", 1, 0, new BigDecimal("5"), null, new BigDecimal("2")));
    var vendors = List.of(new NewApiPricingVendor(1, "OpenAI"));
    CatalogSnapshot snapshot = CatalogSnapshot.build(entries, vendors, "v1", 500_000L);

    CatalogModelDetails details = snapshot.detailsById().get("gpt-4-vision-tool");
    assertThat(details.inputModalities()).isNull();
    assertThat(details.outputModalities()).isNull();
    assertThat(details.capabilities().toolCalling()).isNull();
    assertThat(details.capabilities().reasoning()).isNull();
    assertThat(details.capabilities().structuredOutput()).isNull();
    assertThat(details.capabilities().attachments()).isNull();
    assertThat(details.description()).isNull();
    assertThat(details.tags()).isNull();
    assertThat(details.enhancedPricing()).isNull();
  }

  @Test
  void detailsJsonDoesNotLeakUpstreamInternals() throws Exception {
    var entries =
        List.of(
            new NewApiPricingEntry("m1", 7, 1, null, new BigDecimal("0.001"), null));
    var vendors = List.of(new NewApiPricingVendor(7, "Seven"));
    CatalogSnapshot snapshot = CatalogSnapshot.build(entries, vendors, "v1", 500_000L);

    String json =
        mapper.writeValueAsString(snapshot.detailsById().get("m1")).toLowerCase();
    for (String banned :
        new String[] {
          "vendor_id",
          "vendorid",
          "icon",
          "model_ratio",
          "completion_ratio",
          "enable_groups",
          "usable_group",
          "group_ratio",
          "channel",
          "owner",
          "endpoint",
          "supported_endpoint"
        }) {
      assertThat(json).doesNotContain(banned);
    }
  }
}
