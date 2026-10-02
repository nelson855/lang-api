package com.lang.portal.web.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnhancedPricing {
  private EnhancedPricing() {}

  public static void requireUnique(List<EnhancedPricingItem> items) {
    if (items == null) {
      return;
    }
    Set<String> seen = new HashSet<>();
    for (EnhancedPricingItem item : items) {
      if (item == null) {
        throw new IllegalArgumentException("增强价格项目不合法");
      }
      String key = item.type() + "|" + item.currency() + "|" + item.unit();
      if (!seen.add(key)) {
        throw new IllegalArgumentException("增强价格重复：" + key);
      }
    }
  }
}
