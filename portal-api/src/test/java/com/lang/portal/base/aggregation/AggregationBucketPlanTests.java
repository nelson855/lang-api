package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AggregationBucketPlanTests {

  private AggregationQueryContext context(String startIso, String endIso, String zone, AggregationGranularity granularity) {
    return AggregationQueryContext.of(
        startIso, endIso, zone, granularity, "p2-2026-09-22-a", new PortalCommonProperties.Aggregation());
  }

  @Test
  void fiveMinuteBucketsCoverClippedRange() {
    AggregationQueryContext ctx = context("2026-09-01T00:02:00Z", "2026-09-01T00:13:00Z", "UTC", AggregationGranularity.FIVE_MINUTES);
    List<AggregationBucketPlan.Bucket> buckets = AggregationBucketPlan.plan(ctx);
    assertThat(buckets).hasSize(3);
    assertThat(buckets.get(0).start()).isEqualTo(Instant.parse("2026-09-01T00:02:00Z"));
    assertThat(buckets.get(0).end()).isEqualTo(Instant.parse("2026-09-01T00:05:00Z"));
    assertThat(buckets.get(1).start()).isEqualTo(Instant.parse("2026-09-01T00:05:00Z"));
    assertThat(buckets.get(1).end()).isEqualTo(Instant.parse("2026-09-01T00:10:00Z"));
    assertThat(buckets.get(2).start()).isEqualTo(Instant.parse("2026-09-01T00:10:00Z"));
    assertThat(buckets.get(2).end()).isEqualTo(Instant.parse("2026-09-01T00:13:00Z"));
  }

  @Test
  void emptyRangeStillReturnsFullBuckets() {
    AggregationQueryContext ctx = context("2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", AggregationGranularity.HOUR);
    List<AggregationBucketPlan.Bucket> buckets = AggregationBucketPlan.plan(ctx);
    assertThat(buckets).hasSize(2);
    assertThat(buckets.get(0).start()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(buckets.get(1).start()).isEqualTo(Instant.parse("2026-09-01T01:00:00Z"));
  }

  @Test
  void dstStartDayUses23HourUtcSpan() {
    AggregationQueryContext ctx =
        context("2026-03-08T00:00:00-05:00", "2026-03-09T00:00:00-04:00", "America/New_York", AggregationGranularity.DAY);
    List<AggregationBucketPlan.Bucket> buckets = AggregationBucketPlan.plan(ctx);
    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).start()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
    assertThat(buckets.get(0).end()).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
  }

  @Test
  void dstEndDayUses25HourUtcSpan() {
    AggregationQueryContext ctx =
        context("2026-11-01T00:00:00-04:00", "2026-11-02T00:00:00-05:00", "America/New_York", AggregationGranularity.DAY);
    List<AggregationBucketPlan.Bucket> buckets = AggregationBucketPlan.plan(ctx);
    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).start()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
    assertThat(buckets.get(0).end()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
  }
}
