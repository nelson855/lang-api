package com.lang.portal.web.catalog;

public record EnhancedPricingItem(
    EnhancedPriceType type, String currency, String unit, String price) {

  public EnhancedPricingItem {
    if (type == null) {
      throw new IllegalArgumentException("增强价格类型不合法");
    }
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("增强价格币种不合法");
    }
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("增强价格单位不合法");
    }
    if (!isNonNegativeDecimal(price)) {
      throw new IllegalArgumentException("增强价格金额不合法");
    }
  }

  private static boolean isNonNegativeDecimal(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      return new java.math.BigDecimal(value).compareTo(java.math.BigDecimal.ZERO) >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
