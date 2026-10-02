package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogServiceSnapshotTests {

  private CatalogService serviceWith(
      AtomicInteger loads, PricingSnapshotProvider.Snapshot snapshot, RuntimeException failure) {
    PortalCommonProperties props = new PortalCommonProperties();
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofSeconds(60));
    PricingSnapshotProvider client =
        () -> {
          loads.incrementAndGet();
          if (failure != null) {
            throw failure;
          }
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return snapshot;
        };
    return new CatalogService(
        client,
        props,
        Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build());
  }

  private PricingSnapshotProvider.Snapshot snapshot() {
    return new PricingSnapshotProvider.Snapshot(
        List.of(
            new NewApiPricingEntry("b-model", 1, 1, null, new BigDecimal("0.001"), null),
            new NewApiPricingEntry("a-model", 1, 1, null, new BigDecimal("0.002"), null)),
        List.of(
            new com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor(1, "Example")),
        "v9");
  }

  @Test
  void currentKeepsListCompatible() {
    CatalogService service = serviceWith(new AtomicInteger(), snapshot(), null);
    CatalogData data = service.current();
    assertThat(data.pricingVersion()).isEqualTo("v9");
    assertThat(data.models().stream().map(CatalogModel::id).toList())
        .containsExactly("a-model", "b-model");
  }

  @Test
  void detailsAndProvidersShareSameSnapshot() {
    CatalogService service = serviceWith(new AtomicInteger(), snapshot(), null);
    CatalogData data = service.current();
    var details = service.findDetails("a-model");
    var providers = service.providers();
    assertThat(details).isPresent();
    assertThat(details.orElseThrow().id()).isEqualTo("a-model");
    assertThat(details.orElseThrow().pricing()).isEqualTo(data.models().get(0).pricing());
    assertThat(providers).containsExactly(new ModelProviderOption("Example", "Example", 2));
    assertThat(service.snapshot().pricingVersion()).isEqualTo("v9");
  }

  @Test
  void exactMatchOnly() {
    CatalogService service = serviceWith(new AtomicInteger(), snapshot(), null);
    assertThat(service.findDetails("a-model")).isPresent();
    assertThat(service.findDetails("A-MODEL")).isEmpty();
    assertThat(service.findDetails(" a-model")).isEmpty();
    assertThat(service.findDetails("a-model ")).isEmpty();
    assertThat(service.findDetails("missing")).isEmpty();
  }

  @Test
  void mixedConcurrentReadsLoadOnlyOnce() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    CatalogService service = serviceWith(loads, snapshot(), null);
    ExecutorService pool = Executors.newFixedThreadPool(9);
    CountDownLatch start = new CountDownLatch(1);
    var futures = new java.util.ArrayList<Future<?>>();
    for (int i = 0; i < 3; i++) {
      futures.add(pool.submit(() -> {
        start.await();
        return service.current();
      }));
      futures.add(pool.submit(() -> {
        start.await();
        return service.findDetails("a-model");
      }));
      futures.add(pool.submit(() -> {
        start.await();
        return service.providers();
      }));
    }
    start.countDown();
    for (Future<?> f : futures) {
      assertThat(f.get()).isNotNull();
    }
    pool.shutdown();
    assertThat(loads.get()).isEqualTo(1);
  }

  @Test
  void failureIsNotCached() {
    AtomicInteger loads = new AtomicInteger();
    RuntimeException failure =
        new com.lang.portal.base.exception.UpstreamException(
            com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_ERROR);
    CatalogService failing = serviceWith(loads, snapshot(), failure);
    assertThatThrownBy(failing::current).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(failing::current).isInstanceOf(RuntimeException.class);
    assertThat(loads.get()).isEqualTo(2);
  }

  @Test
  void ttlExpiryTriggersReload() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    PortalCommonProperties props = new PortalCommonProperties();
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofMillis(50));
    PricingSnapshotProvider.Snapshot data = snapshot();
    PricingSnapshotProvider client =
        () -> {
          loads.incrementAndGet();
          return data;
        };
    CatalogService service =
        new CatalogService(
            client,
            props,
            Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMillis(50))
                .build());
    service.current();
    Thread.sleep(120);
    service.current();
    assertThat(loads.get()).isEqualTo(2);
  }

  @Test
  void expiredRefreshFailureDoesNotServeStale() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    PortalCommonProperties props = new PortalCommonProperties();
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofMillis(50));
    PricingSnapshotProvider.Snapshot data = snapshot();
    RuntimeException failure =
        new com.lang.portal.base.exception.UpstreamException(
            com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_ERROR);
    PricingSnapshotProvider client =
        () -> {
          int n = loads.incrementAndGet();
          if (n == 1) {
            return data;
          }
          throw failure;
        };
    CatalogService service =
        new CatalogService(
            client,
            props,
            Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMillis(50))
                .build());
    assertThat(service.current().models()).hasSize(2);
    Thread.sleep(120);
    assertThatThrownBy(service::current).isInstanceOf(RuntimeException.class);
    assertThat(loads.get()).isEqualTo(2);
  }
}
