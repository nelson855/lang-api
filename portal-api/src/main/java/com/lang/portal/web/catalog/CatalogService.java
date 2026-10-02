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
  private final Cache<String, CatalogSnapshot> cache;

  @Autowired
  public CatalogService(PricingSnapshotProvider pricingClient, PortalCommonProperties properties) {
    this(pricingClient, properties, buildCache(properties));
  }

  CatalogService(
      PricingSnapshotProvider pricingClient,
      PortalCommonProperties properties,
      Cache<String, CatalogSnapshot> cache) {
    this.pricingClient = pricingClient;
    this.properties = properties;
    this.cache = cache;
  }

  private static Cache<String, CatalogSnapshot> buildCache(PortalCommonProperties properties) {
    return Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(properties.catalog().cacheTtl())
        .build();
  }

  public CatalogData current() {
    return snapshot().data();
  }

  public CatalogSnapshot snapshot() {
    return cache.get("catalog", k -> load());
  }

  public java.util.Optional<CatalogModelDetails> findDetails(String id) {
    if (id == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(snapshot().detailsById().get(id));
  }

  public java.util.List<ModelProviderOption> providers() {
    return snapshot().providers();
  }

  private CatalogSnapshot load() {
    PricingSnapshotProvider.Snapshot snapshot = pricingClient.fetchSnapshot();
    CatalogSnapshot data = CatalogSnapshot.build(
        snapshot.entries(), snapshot.vendors(), snapshot.pricingVersion(),
        properties.catalog().quotaPerUsd());
    log.info("event=catalog_loaded models={}", data.data().models().size());
    return data;
  }
}
