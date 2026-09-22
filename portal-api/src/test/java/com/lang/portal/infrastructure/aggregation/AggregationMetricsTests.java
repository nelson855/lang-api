package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AggregationMetricsTests {

  private AggregationMetrics metrics() {
    return new AggregationMetrics(new SimpleMeterRegistry());
  }

  @Test
  void aggregationDurationCarriesOperationAndOutcome() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordAggregation("usage-summary", AggregationOutcome.SUCCESS, Duration.ofMillis(120));

    var timer = registry.get("portal.aggregation.duration").tags("operation", "usage-summary", "outcome", "success").timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0);
  }

  @Test
  void upstreamCountsCarrySourceAndOutcome() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordUpstream(AggregationSource.SUCCESS_LOG, AggregationOutcome.SUCCESS, 2, 40);

    assertThat(registry.get("portal.aggregation.upstream.calls").tags("source", "success-log", "outcome", "success").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("portal.aggregation.upstream.pages").tags("source", "success-log", "outcome", "success").counter().count()).isEqualTo(2.0);
    assertThat(registry.get("portal.aggregation.upstream.records").tags("source", "success-log", "outcome", "success").counter().count()).isEqualTo(40.0);
  }

  @Test
  void cacheEventsDistinguishHitMissAndCoalesced() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordCache("usage-summary", CacheOutcome.HIT);
    metrics.recordCache("usage-summary", CacheOutcome.MISS);
    metrics.recordCache("usage-summary", CacheOutcome.COALESCED);

    assertThat(registry.get("portal.aggregation.cache").tags("operation", "usage-summary", "cacheOutcome", "hit").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("portal.aggregation.cache").tags("operation", "usage-summary", "cacheOutcome", "miss").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("portal.aggregation.cache").tags("operation", "usage-summary", "cacheOutcome", "coalesced").counter().count()).isEqualTo(1.0);
  }

  @Test
  void protectionRejectionsCarryFiniteReason() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordProtection("usage-summary", ProtectReason.RECORDS);

    assertThat(registry.get("portal.aggregation.rejections").tags("operation", "usage-summary", "reason", "records").counter().count()).isEqualTo(1.0);
  }
}
