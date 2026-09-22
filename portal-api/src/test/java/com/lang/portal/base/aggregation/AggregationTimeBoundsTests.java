package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregationTimeBoundsTests {

  private AggregationQueryContext context(String startIso, String endIso) {
    return AggregationQueryContext.of(
        startIso,
        endIso,
        "UTC",
        AggregationGranularity.HOUR,
        "p2-2026-09-22-a",
        new PortalCommonProperties.Aggregation());
  }

  @Test
  void rangeIsStartInclusiveEndExclusive() {
    AggregationQueryContext ctx = context("2026-09-01T00:00:00Z", "2026-09-01T01:00:00Z");
    assertThat(AggregationTimeBounds.contains(ctx, Instant.parse("2026-09-01T00:00:00Z"))).isTrue();
    assertThat(AggregationTimeBounds.contains(ctx, Instant.parse("2026-09-01T00:59:59Z"))).isTrue();
    assertThat(AggregationTimeBounds.contains(ctx, Instant.parse("2026-09-01T01:00:00Z"))).isFalse();
  }

  @Test
  void adjacentRangesDoNotShareBoundaryRecord() {
    AggregationQueryContext first = context("2026-09-01T00:00:00Z", "2026-09-01T01:00:00Z");
    AggregationQueryContext second = context("2026-09-01T01:00:00Z", "2026-09-01T02:00:00Z");
    Instant boundary = Instant.parse("2026-09-01T01:00:00Z");
    assertThat(AggregationTimeBounds.contains(first, boundary)).isFalse();
    assertThat(AggregationTimeBounds.contains(second, boundary)).isTrue();
  }

  @Test
  void upstreamInclusiveEndIsOneSecondBeforeExclusiveEnd() {
    AggregationQueryContext ctx = context("2026-09-01T00:00:00Z", "2026-09-01T01:00:00Z");
    assertThat(AggregationTimeBounds.upstreamInclusiveEnd(ctx))
        .isEqualTo(Instant.parse("2026-09-01T00:59:59Z"));
  }
}
