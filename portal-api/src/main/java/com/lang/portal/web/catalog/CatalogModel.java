package com.lang.portal.web.catalog;

public record CatalogModel(
    String id, String displayName, String provider, String availability, CatalogPricing pricing) {}
