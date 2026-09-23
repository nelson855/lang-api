package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.infrastructure.aggregation.AggregationOutcome;
import com.lang.portal.infrastructure.aggregation.AggregationSource;
import com.lang.portal.infrastructure.aggregation.CacheOutcome;
import com.lang.portal.infrastructure.aggregation.ProtectReason;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class DashboardStatsMetricsTests {

  @Test
  void dashboardOperationUsesLowCardinalityTagsOnly() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordAggregation("dashboard-stats", AggregationOutcome.SUCCESS, Duration.ofMillis(20));
    metrics.recordUpstream(AggregationSource.SUCCESS_LOG, AggregationOutcome.SUCCESS, 2, 3);
    metrics.recordCache("dashboard-stats", CacheOutcome.HIT);

    Set<String> tagKeys =
        StreamSupport.stream(registry.getMeters().spliterator(), false)
            .flatMap(m -> m.getId().getTags().stream())
            .map(Tag::getKey)
            .collect(Collectors.toSet());
    assertThat(tagKeys).isSubsetOf(Set.of("operation", "outcome", "source", "cacheOutcome", "reason"));

    Set<String> tagValues =
        StreamSupport.stream(registry.getMeters().spliterator(), false)
            .flatMap(m -> m.getId().getTags().stream())
            .map(Tag::getValue)
            .collect(Collectors.toSet());
    assertThat(tagValues).doesNotContain("user-42", "gpt-test", "req-1", "2026-09-01T00:00:00Z");
    assertThat(tagValues).contains("dashboard-stats");
    for (Meter meter : registry.getMeters()) {
      assertThat(meter.getId().getName()).startsWith("portal.aggregation.");
    }
    metrics.recordProtection("dashboard-stats", ProtectReason.DEADLINE);
  }
}
