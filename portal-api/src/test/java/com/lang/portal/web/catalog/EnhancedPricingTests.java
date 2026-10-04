package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnhancedPricingTests {

  @Test
  void singleItemRequiresAllFields() {
    assertThatThrownBy(
            () ->
                new EnhancedPricingItem(
                    null, "USD", "PER_REQUEST", "0.001"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new EnhancedPricingItem(EnhancedPriceType.SEARCH, " ", "PER_REQUEST", "0.001"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", null, "0.001"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void priceMustBeNonNegativeDecimalString() {
    assertThatThrownBy(
            () ->
                new EnhancedPricingItem(
                    EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "-0.001"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validItemKeepsValues() {
    EnhancedPricingItem item =
        new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "0.001");
    assertThat(item.type()).isEqualTo(EnhancedPriceType.SEARCH);
    assertThat(item.price()).isEqualTo("0.001");
  }

  @Test
  void duplicateTypeCurrencyUnitMustFail() {
    var first =
        new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "0.001");
    var duplicate =
        new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "0.002");
    assertThatThrownBy(() -> EnhancedPricing.requireUnique(List.of(first, duplicate)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void differentUnitIsNotDuplicate() {
    var first =
        new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_REQUEST", "0.001");
    var other =
        new EnhancedPricingItem(EnhancedPriceType.SEARCH, "USD", "PER_MILLION_TOKENS", "0.002");
    EnhancedPricing.requireUnique(List.of(first, other));
  }
}
