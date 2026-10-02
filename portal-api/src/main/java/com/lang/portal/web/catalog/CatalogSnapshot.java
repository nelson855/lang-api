package com.lang.portal.web.catalog;

import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record CatalogSnapshot(
    String pricingVersion,
    CatalogData data,
    Map<String, CatalogModelDetails> detailsById,
    List<ModelProviderOption> providers) {

  public static CatalogSnapshot build(
      List<NewApiPricingEntry> entries,
      List<NewApiPricingVendor> vendors,
      String pricingVersion,
      long quotaPerUsd) {
    CatalogData data =
        CatalogPriceMapper.mapSnapshot(entries, vendors, pricingVersion, quotaPerUsd);

    Map<String, CatalogModelDetails> details = new HashMap<>();
    for (CatalogModel model : data.models()) {
      CatalogModelDetails detail = CatalogModelDetails.baseline(model);
      if (detail.enhancedPricing() != null) {
        EnhancedPricing.requireUnique(detail.enhancedPricing());
      }
      if (details.putIfAbsent(detail.id(), detail) != null) {
        throw new IllegalArgumentException("重复模型 ID：" + detail.id());
      }
    }

    Map<String, Integer> counts = new TreeMap<>();
    for (CatalogModel model : data.models()) {
      String provider = model.provider();
      if (provider == null) {
        continue;
      }
      String name = provider.trim();
      if (name.isEmpty()) {
        continue;
      }
      counts.merge(name, 1, Integer::sum);
    }
    List<ModelProviderOption> providersOut = new ArrayList<>();
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      providersOut.add(new ModelProviderOption(e.getKey(), e.getKey(), e.getValue()));
    }
    providersOut.sort(Comparator.comparing(ModelProviderOption::value));

    return new CatalogSnapshot(
        data.pricingVersion(),
        data,
        Map.copyOf(details),
        List.copyOf(providersOut));
  }
}
