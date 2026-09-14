package com.lang.portal.web.catalog;

import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogPriceMapper {
  private CatalogPriceMapper() {}

  public record MapResult(CatalogModel model) {}

  public static MapResult mapEntry(
      NewApiPricingEntry entry, Map<Integer, String> vendors, long quotaPerUsd) {
    String id = entry == null ? null : entry.modelName();
    if (id != null) {
      id = id.trim();
    }
    if (!isValidModelId(id)) {
      throw new IllegalArgumentException("非法模型标识");
    }
    String provider = null;
    if (entry.vendorId() != null) {
      String name = vendors.get(entry.vendorId());
      if (name != null && !name.isBlank()) {
        provider = name.trim();
      }
    }
    CatalogPricing pricing = toPricing(entry, quotaPerUsd);
    CatalogModel model = new CatalogModel(id, null, provider, "AVAILABLE", pricing);
    return new MapResult(model);
  }

  public static CatalogData mapSnapshot(
      List<NewApiPricingEntry> entries,
      List<NewApiPricingVendor> vendors,
      String pricingVersion,
      long quotaPerUsd) {
    Map<Integer, String> vendorMap = new HashMap<>();
    if (vendors != null) {
      Map<Integer, Integer> counts = new HashMap<>();
      for (NewApiPricingVendor v : vendors) {
        if (v == null || v.id() == null) {
          continue;
        }
        counts.merge(v.id(), 1, Integer::sum);
      }
      for (NewApiPricingVendor v : vendors) {
        if (v == null || v.id() == null || v.name() == null || v.name().isBlank()) {
          continue;
        }
        if (counts.getOrDefault(v.id(), 0) == 1) {
          vendorMap.putIfAbsent(v.id(), v.name().trim());
        }
      }
    }
    List<CatalogModel> models = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (entries != null) {
      for (NewApiPricingEntry entry : entries) {
        MapResult r = mapEntry(entry, vendorMap, quotaPerUsd);
        if (!seen.add(r.model().id())) {
          throw new IllegalArgumentException("重复模型 ID：" + r.model().id());
        }
        models.add(r.model());
      }
    }
    models.sort(Comparator.comparing(CatalogModel::id));
    return new CatalogData(pricingVersion, List.copyOf(models));
  }

  private static CatalogPricing toPricing(NewApiPricingEntry entry, long quotaPerUsd) {
    try {
      if (entry.quotaType() != null && entry.quotaType() == 1) {
        if (entry.modelPrice() == null || !isFiniteNonNegative(entry.modelPrice())) {
          return null;
        }
        return new CatalogPricing(
            "REQUEST", "USD", "PER_REQUEST", null, null, format(entry.modelPrice()));
      }
      if (entry.quotaType() == null || entry.quotaType() == 0) {
        if (entry.modelRatio() == null
            || entry.completionRatio() == null
            || !isFiniteNonNegative(entry.modelRatio())
            || !isFiniteNonNegative(entry.completionRatio())
            || quotaPerUsd <= 0) {
          return null;
        }
        BigDecimal input = entry.modelRatio()
            .multiply(BigDecimal.valueOf(1_000_000))
            .divide(BigDecimal.valueOf(quotaPerUsd), 10, RoundingMode.HALF_UP);
        BigDecimal output = input.multiply(entry.completionRatio());
        return new CatalogPricing(
            "TOKEN", "USD", "PER_MILLION_TOKENS", format(input), format(output), null);
      }
      return null;
    } catch (ArithmeticException e) {
      return null;
    }
  }

  private static boolean isFiniteNonNegative(BigDecimal v) {
    return v.compareTo(BigDecimal.ZERO) >= 0;
  }

  private static String format(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() < 1) {
      stripped = stripped.setScale(1);
    } else if (stripped.scale() > 6) {
      stripped = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
      if (stripped.scale() < 1) {
        stripped = stripped.setScale(1);
      }
    }
    return stripped.toPlainString();
  }

  static boolean isValidModelId(String id) {
    if (id == null || id.isEmpty() || id.length() > 128) {
      return false;
    }
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c <= 0x1F || c == 0x7F) {
        return false;
      }
    }
    return true;
  }
}
