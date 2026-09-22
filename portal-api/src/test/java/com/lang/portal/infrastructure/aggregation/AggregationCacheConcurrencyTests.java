package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AggregationCacheConcurrencyTests {

  private AggregationQueryContext context(String startIso, String endIso, String zone) {
    return AggregationQueryContext.of(
        startIso,
        endIso,
        zone,
        AggregationGranularity.HOUR,
        "p2-2026-09-22-a",
        new PortalCommonProperties.Aggregation());
  }

  @Test
  void concurrentMissOnSameKeyLoadsOnlyOnce() throws Exception {
    AggregationCache<List<String>> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey key =
        AggregationCacheKey.of(
            "usage-summary", "user-42", context("2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC"), Map.of());
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch releaseLoader = new CountDownLatch(1);
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<List<String>>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () ->
                    cache.get(
                        key,
                        k -> {
                          loads.incrementAndGet();
                          try {
                            if (!releaseLoader.await(10, TimeUnit.SECONDS)) {
                              throw new IllegalStateException("加载器等待超时");
                            }
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                          }
                          return List.of("shared");
                        })));
      }
      long spinUntil = System.currentTimeMillis() + 5000;
      while (loads.get() == 0 && System.currentTimeMillis() < spinUntil) {
        Thread.sleep(10);
      }
      assertThat(loads.get()).isEqualTo(1);
      Thread.sleep(300);
      releaseLoader.countDown();
      List<List<String>> results = new ArrayList<>();
      for (Future<List<String>> future : futures) {
        results.add(future.get(10, TimeUnit.SECONDS));
      }
      assertThat(loads.get()).isEqualTo(1);
      for (List<String> result : results) {
        assertThat(result).isSameAs(results.get(0));
        assertThat(result).containsExactly("shared");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void differentIdentityDimensionsNeverShareLoads() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AtomicInteger loads = new AtomicInteger();
    AggregationQueryContext shanghaiDay =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00+08:00",
            "2026-09-02T00:00:00+08:00",
            "Asia/Shanghai",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());
    AggregationQueryContext utcSameInstants =
        AggregationQueryContext.of(
            "2026-08-31T16:00:00Z",
            "2026-09-01T16:00:00Z",
            "UTC",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());

    cache.get(AggregationCacheKey.of("usage-summary", "user-42", shanghaiDay, Map.of("model", "a")),
        k -> "v" + loads.incrementAndGet());
    cache.get(AggregationCacheKey.of("usage-summary", "user-7", shanghaiDay, Map.of("model", "a")),
        k -> "v" + loads.incrementAndGet());
    cache.get(AggregationCacheKey.of("usage-summary", "user-42", utcSameInstants, Map.of("model", "a")),
        k -> "v" + loads.incrementAndGet());
    cache.get(AggregationCacheKey.of("usage-summary", "user-42", shanghaiDay, Map.of("model", "b")),
        k -> "v" + loads.incrementAndGet());
    AggregationCacheKey shanghaiKey =
        AggregationCacheKey.of("usage-summary", "user-42", shanghaiDay, Map.of("model", "a"));
    AggregationCacheKey futureBaselineKey =
        new AggregationCacheKey(
            shanghaiKey.operation(),
            shanghaiKey.userId(),
            shanghaiKey.start(),
            shanghaiKey.end(),
            shanghaiKey.granularity(),
            shanghaiKey.zoneId(),
            "p2-future",
            shanghaiKey.filters());
    cache.get(futureBaselineKey, k -> "v" + loads.incrementAndGet());

    assertThat(loads.get()).isEqualTo(5);
  }
}
