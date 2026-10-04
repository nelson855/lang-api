package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AggregationCacheTests {

  static final class FakeTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }

    @Override
    public long read() {
      return nanos.get();
    }
  }

  private AggregationCacheKey key(String user) {
    AggregationQueryContext context =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00Z",
            "2026-09-01T02:00:00Z",
            "UTC",
            AggregationGranularity.HOUR,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());
    return AggregationCacheKey.of("usage-summary", user, context, Map.of());
  }

  private static List<AggregationLogRecord> projection(String requestId) {
    return List.of(
        new AggregationLogRecord(
            Instant.parse("2026-09-01T00:00:00Z"),
            AggregationLogResult.SUCCESS,
            9L,
            "gpt-test",
            requestId,
            "probe-key-01",
            10L,
            20L,
            3000L,
            false,
            500L));
  }

  @Test
  void missLoadsAndHitReusesSameReadOnlyResult() {
    AggregationCache<List<AggregationLogRecord>> cache =
        new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AtomicInteger loads = new AtomicInteger();
    AggregationCacheKey cacheKey = key("user-42");

    List<AggregationLogRecord> first = cache.get(cacheKey, k -> {
      loads.incrementAndGet();
      return projection("req-1");
    });
    List<AggregationLogRecord> second = cache.get(cacheKey, k -> {
      loads.incrementAndGet();
      return projection("req-2");
    });

    assertThat(loads.get()).isEqualTo(1);
    assertThat(second).isSameAs(first);
    assertThat(second.get(0).requestId()).isEqualTo("req-1");
  }

  @Test
  void expiredEntryReloadsAfterTtl() {
    FakeTicker ticker = new FakeTicker();
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000, ticker);
    AggregationCacheKey cacheKey = key("user-42");
    AtomicInteger loads = new AtomicInteger();

    cache.get(cacheKey, k -> {
      loads.incrementAndGet();
      return "v1";
    });
    cache.get(cacheKey, k -> {
      loads.incrementAndGet();
      return "v2";
    });
    assertThat(loads.get()).isEqualTo(1);

    ticker.advance(Duration.ofSeconds(31));
    String reloaded = cache.get(cacheKey, k -> {
      loads.incrementAndGet();
      return "v2";
    });
    assertThat(loads.get()).isEqualTo(2);
    assertThat(reloaded).isEqualTo("v2");
  }

  @Test
  void maximumSizeComesFromConfigAndBoundsEntries() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 2);
    assertThat(cache.evictionMaximum()).contains(2L);

    cache.get(key("user-1"), k -> "v1");
    cache.get(key("user-2"), k -> "v2");
    cache.get(key("user-3"), k -> "v3");
    cache.cleanUp();
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
  }

  @Test
  void factoryAppliesSharedConservativeDefaults() {
    AggregationCache<String> cache =
        AggregationCache.fromAggregationConfig(new PortalCommonProperties.Aggregation());
    assertThat(cache.evictionMaximum()).contains(1000L);
  }
}
