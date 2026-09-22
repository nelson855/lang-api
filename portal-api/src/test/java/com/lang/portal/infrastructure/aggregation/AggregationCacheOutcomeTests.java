package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AggregationCacheOutcomeTests {

  private AggregationCacheKey key() {
    return new AggregationCacheKey(
        "usage-summary",
        "user-42",
        java.time.Instant.parse("2026-09-01T00:00:00Z"),
        java.time.Instant.parse("2026-09-01T02:00:00Z"),
        com.lang.portal.base.aggregation.AggregationGranularity.HOUR,
        "UTC",
        "p2-2026-09-22-a",
        Map.of());
  }

  @Test
  void hitReportsHitWithoutLoading() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey cacheKey = key();
    cache.get(cacheKey, k -> "v");

    AggregationCache.LoadResult<String> result =
        cache.getWithOutcome(
            cacheKey,
            k -> {
              throw new IllegalStateException("命中不得重复加载");
            });

    assertThat(result.outcome()).isEqualTo(CacheOutcome.HIT);
    assertThat(result.value()).isEqualTo("v");
  }

  @Test
  void absentKeyReportsMiss() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);

    AggregationCache.LoadResult<String> result = cache.getWithOutcome(key(), k -> "v");

    assertThat(result.outcome()).isEqualTo(CacheOutcome.MISS);
    assertThat(result.value()).isEqualTo("v");
  }

  @Test
  void concurrentWaiterReportsCoalesced() throws Exception {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey cacheKey = key();
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch loaderEntered = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    AtomicReference<AggregationCache.LoadResult<String>> waiterResult = new AtomicReference<>();

    Thread loader =
        new Thread(
            () ->
                cache.getWithOutcome(
                    cacheKey,
                    k -> {
                      loads.incrementAndGet();
                      loaderEntered.countDown();
                      try {
                        if (!releaseLoader.await(10, TimeUnit.SECONDS)) {
                          throw new IllegalStateException("加载器等待超时");
                        }
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                      }
                      return "shared";
                    }));
    Thread waiter =
        new Thread(
            () ->
                waiterResult.set(
                    cache.getWithOutcome(
                        cacheKey,
                        k -> {
                          throw new IllegalStateException("同键并发不得重复加载");
                        })));
    loader.start();
    assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
    waiter.start();
    Thread.sleep(300);
    releaseLoader.countDown();
    loader.join(5000);
    waiter.join(5000);

    assertThat(loads.get()).isEqualTo(1);
    assertThat(waiterResult.get().outcome()).isEqualTo(CacheOutcome.COALESCED);
    assertThat(waiterResult.get().value()).isEqualTo("shared");
  }
}
