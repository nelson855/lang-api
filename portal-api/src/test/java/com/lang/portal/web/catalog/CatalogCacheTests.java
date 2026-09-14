package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.web.catalog.PricingSnapshotProvider;
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

class CatalogCacheTests {

  private CatalogService serviceWith(AtomicInteger loads, PricingSnapshotProvider.Snapshot snapshot) {
    PortalCommonProperties props = new PortalCommonProperties();
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofSeconds(60));
    PricingSnapshotProvider client = new PricingSnapshotProvider() {
      @Override
      public Snapshot fetchSnapshot() {
        loads.incrementAndGet();
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return snapshot;
      }
    };
    return new CatalogService(client, props,
        Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build());
  }

  @Test
  void concurrentMissLoadsOnlyOnce() throws Exception {
    var snapshot = new PricingSnapshotProvider.Snapshot(
        List.of(new NewApiPricingEntry("m", null, 1, null, new BigDecimal("0.001"), null)),
        List.of(), null);
    AtomicInteger loads = new AtomicInteger();
    CatalogService service = serviceWith(loads, snapshot);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<CatalogData>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < 8; i++) {
      futures.add(pool.submit(() -> {
        start.await();
        return service.current();
      }));
    }
    start.countDown();
    for (Future<CatalogData> f : futures) {
      assertThat(f.get().models()).hasSize(1);
    }
    pool.shutdown();
    assertThat(loads.get()).isEqualTo(1);
  }
}
