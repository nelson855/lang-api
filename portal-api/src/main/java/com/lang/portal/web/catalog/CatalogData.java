package com.lang.portal.web.catalog;

import java.util.List;

public record CatalogData(String pricingVersion, List<CatalogModel> models) {}
