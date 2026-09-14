package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogPricingTests {

  private static final long QUOTA_PER_USD = 500_000L;

  @Test
  void tokenModeConvertsBasePrices() {
    NewApiPricingEntry entry = new NewApiPricingEntry(
        "gpt-example", 1, 0, new BigDecimal("5"), null, new BigDecimal("2"));
    CatalogPriceMapper.MapResult result = CatalogPriceMapper.mapEntry(
        entry, java.util.Map.of(1, "Example"), QUOTA_PER_USD);
    assertThat(result.model().id()).isEqualTo("gpt-example");
    assertThat(result.model().provider()).isEqualTo("Example");
    assertThat(result.model().availability()).isEqualTo("AVAILABLE");
    assertThat(result.model().pricing()).isNotNull();
    assertThat(result.model().pricing().mode()).isEqualTo("TOKEN");
    assertThat(result.model().pricing().currency()).isEqualTo("USD");
    assertThat(result.model().pricing().unit()).isEqualTo("PER_MILLION_TOKENS");
    assertThat(result.model().pricing().input()).isEqualTo("10.0");
    assertThat(result.model().pricing().output()).isEqualTo("20.0");
    assertThat(result.model().pricing().request()).isNull();
  }

  @Test
  void requestModeUsesModelPrice() {
    NewApiPricingEntry entry = new NewApiPricingEntry(
        "once-model", null, 1, null, new BigDecimal("0.003"), null);
    CatalogPriceMapper.MapResult result = CatalogPriceMapper.mapEntry(
        entry, java.util.Map.of(), QUOTA_PER_USD);
    assertThat(result.model().pricing().mode()).isEqualTo("REQUEST");
    assertThat(result.model().pricing().unit()).isEqualTo("PER_REQUEST");
    assertThat(result.model().pricing().request()).isEqualTo("0.003");
    assertThat(result.model().pricing().input()).isNull();
    assertThat(result.model().pricing().output()).isNull();
  }

  @Test
  void illegalPriceKeepsModelWithNullPricing() {
    NewApiPricingEntry entry = new NewApiPricingEntry(
        "bad-price", null, 0, new BigDecimal("-1"), null, new BigDecimal("1"));
    CatalogPriceMapper.MapResult result = CatalogPriceMapper.mapEntry(
        entry, java.util.Map.of(), QUOTA_PER_USD);
    assertThat(result.model().id()).isEqualTo("bad-price");
    assertThat(result.model().pricing()).isNull();
  }

  @Test
  void snapshotSortsByModelIdAndRejectsDuplicates() {
    var entries = List.of(
        new NewApiPricingEntry("b-model", null, 1, null, new BigDecimal("0.001"), null),
        new NewApiPricingEntry("a-model", null, 1, null, new BigDecimal("0.002"), null));
    var snapshot = CatalogPriceMapper.mapSnapshot(entries, List.of(), "v1", QUOTA_PER_USD);
    assertThat(snapshot.models().stream().map(CatalogModel::id).toList())
        .containsExactly("a-model", "b-model");

    var dup = List.of(
        new NewApiPricingEntry("dup", null, 1, null, new BigDecimal("0.001"), null),
        new NewApiPricingEntry("dup", null, 1, null, new BigDecimal("0.001"), null));
    assertThatThrownBy(() -> CatalogPriceMapper.mapSnapshot(dup, List.of(), "v1", QUOTA_PER_USD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void vendorOnlyReturnedOnUniqueMatch() {
    var entries = List.of(
        new NewApiPricingEntry("m1", 7, 1, null, new BigDecimal("0.001"), null));
    var vendors = List.of(new NewApiPricingVendor(7, "Seven"));
    var snapshot = CatalogPriceMapper.mapSnapshot(entries, vendors, null, QUOTA_PER_USD);
    assertThat(snapshot.models().get(0).provider()).isEqualTo("Seven");
  }
}
