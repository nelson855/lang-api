package com.lang.portal.web.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lang.portal.config.PortalCommonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

  private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

  private final PricingSnapshotProvider pricingClient;
  private final PortalCommonProperties properties;
  private final Cache<String, CatalogData> cache;

  @Autowired
  public CatalogService(PricingSnapshotProvider pricingClient, PortalCommonProperties properties) {
    this(pricingClient, properties, buildCache(properties));
  }

  CatalogService(
      PricingSnapshotProvider pricingClient, PortalCommonProperties properties, Cache<String, CatalogData> cache) {
    this.pricingClient = pricingClient;
    this.properties = properties;
    this.cache = cache;
  }

  private static Cache<String, CatalogData> buildCache(PortalCommonProperties properties) {
    return Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(properties.catalog().cacheTtl())
        .build();
  }

  public CatalogData current() {
    return cache.get("catalog", k -> load());
  }

  private CatalogData load() {
    PricingSnapshotProvider.Snapshot snapshot = pricingClient.fetchSnapshot();
    CatalogData data = CatalogPriceMapper.mapSnapshot(
        snapshot.entries(), snapshot.vendors(), snapshot.pricingVersion(),
        properties.catalog().quotaPerUsd());
    log.info("event=catalog_loaded models={}", data.models().size());
    return data;
  }
}
