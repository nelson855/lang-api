package com.lang.portal.web.catalog;

import java.util.List;

public record CatalogModelDetails(
    String id,
    String displayName,
    String provider,
    String availability,
    CatalogPricing pricing,
    Integer contextWindowTokens,
    Integer maxOutputTokens,
    List<ModelModality> inputModalities,
    List<ModelModality> outputModalities,
    CatalogCapabilities capabilities,
    String releaseDate,
    String description,
    List<String> tags,
    Integer sortOrder,
    List<EnhancedPricingItem> enhancedPricing) {

  public static CatalogModelDetails baseline(CatalogModel model) {
    return new CatalogModelDetails(
        model.id(),
        model.displayName(),
        model.provider(),
        model.availability(),
        model.pricing(),
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
  }
}
