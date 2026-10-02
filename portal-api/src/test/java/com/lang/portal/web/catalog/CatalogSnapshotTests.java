package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogSnapshotTests {

  private static final long QUOTA_PER_USD = 500_000L;

  private static NewApiPricingEntry requestModel(String name, Integer vendorId) {
    return new NewApiPricingEntry(name, vendorId, 1, null, new BigDecimal("0.001"), null);
  }

  @Test
  void buildsListDetailsProvidersAndVersionAtomically() {
    var entries =
        List.of(
            requestModel("b-deep", 1),
            requestModel("a-open", 2),
            requestModel("c-deep", 1),
            requestModel("d-unknown", null));
    var vendors =
        List.of(new NewApiPricingVendor(1, "DeepSeek"), new NewApiPricingVendor(2, "OpenAI"));

    CatalogSnapshot snapshot =
        CatalogSnapshot.build(entries, vendors, "p2-2026-09-22-a", QUOTA_PER_USD);

    assertThat(snapshot.pricingVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(snapshot.data().pricingVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(snapshot.data().models().stream().map(CatalogModel::id).toList())
        .containsExactly("a-open", "b-deep", "c-deep", "d-unknown");
    assertThat(snapshot.detailsById()).hasSize(4);
    assertThat(snapshot.detailsById().get("a-open").provider()).isEqualTo("OpenAI");
    assertThat(snapshot.detailsById().get("a-open").pricing())
        .isEqualTo(snapshot.data().models().stream().filter(m -> m.id().equals("a-open")).findFirst().orElseThrow().pricing());
    assertThat(snapshot.providers()).containsExactly(
        new ModelProviderOption("DeepSeek", "DeepSeek", 2),
        new ModelProviderOption("OpenAI", "OpenAI", 1));
  }

  @Test
  void sameNameFromDifferentVendorIdsMerges() {
    var entries = List.of(requestModel("m1", 1), requestModel("m2", 2));
    var vendors =
        List.of(new NewApiPricingVendor(1, "DeepSeek"), new NewApiPricingVendor(2, " DeepSeek "));

    CatalogSnapshot snapshot = CatalogSnapshot.build(entries, vendors, "v1", QUOTA_PER_USD);

    assertThat(snapshot.providers())
        .containsExactly(new ModelProviderOption("DeepSeek", "DeepSeek", 2));
  }

  @Test
  void caseSensitiveNamesStaySeparateAndUnknownExcluded() {
    var entries =
        List.of(
            requestModel("m1", 1), requestModel("m2", 2), requestModel("m3", null));
    var vendors =
        List.of(new NewApiPricingVendor(1, "OpenAI"), new NewApiPricingVendor(2, "openai"));

    CatalogSnapshot snapshot = CatalogSnapshot.build(entries, vendors, "v1", QUOTA_PER_USD);

    assertThat(snapshot.providers())
        .containsExactly(
            new ModelProviderOption("OpenAI", "OpenAI", 1),
            new ModelProviderOption("openai", "openai", 1));
  }

  @Test
  void emptyProvidersWhenNoKnownSupplier() {
    var entries = List.of(requestModel("m1", null));
    CatalogSnapshot snapshot = CatalogSnapshot.build(entries, List.of(), "v1", QUOTA_PER_USD);
    assertThat(snapshot.providers()).isEmpty();
    assertThat(snapshot.detailsById()).hasSize(1);
  }

  @Test
  void duplicateOrIllegalIdFailsWholeSnapshot() {
    var dup = List.of(requestModel("dup", null), requestModel("dup", null));
    assertThatThrownBy(() -> CatalogSnapshot.build(dup, List.of(), "v1", QUOTA_PER_USD))
        .isInstanceOf(IllegalArgumentException.class);

    var illegal =
        List.of(
            new NewApiPricingEntry("badid", null, 1, null, new BigDecimal("0.001"), null));
    assertThatThrownBy(() -> CatalogSnapshot.build(illegal, List.of(), "v1", QUOTA_PER_USD))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
