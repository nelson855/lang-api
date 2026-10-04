package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregationQueryContextTests {

  private PortalCommonProperties.Aggregation limits() {
    return new PortalCommonProperties.Aggregation();
  }

  @Test
  void validExplicitRangeIsNormalizedToUtc() {
    AggregationQueryContext first =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00+08:00",
            "2026-09-02T00:00:00+08:00",
            "Asia/Shanghai",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            limits());
    AggregationQueryContext second =
        AggregationQueryContext.of(
            "2026-08-31T16:00:00Z",
            "2026-09-01T16:00:00Z",
            "Asia/Shanghai",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            limits());
    assertThat(first.start()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
    assertThat(first.end()).isEqualTo(Instant.parse("2026-09-01T16:00:00Z"));
    assertThat(first.zone().getId()).isEqualTo("Asia/Shanghai");
    assertThat(second.start()).isEqualTo(first.start());
    assertThat(second.end()).isEqualTo(first.end());
  }

  @Test
  void missingStartTimeIsRejectedBeforeUpstream() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    null,
                    "2026-09-02T00:00:00+08:00",
                    "Asia/Shanghai",
                    AggregationGranularity.DAY,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonIncreasingRangeIsRejected() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-02T00:00:00+08:00",
                    "2026-09-02T00:00:00+08:00",
                    "Asia/Shanghai",
                    AggregationGranularity.DAY,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void subSecondPrecisionIsRejected() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-01T00:00:00.123+08:00",
                    "2026-09-02T00:00:00+08:00",
                    "Asia/Shanghai",
                    AggregationGranularity.DAY,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownTimezoneIsRejected() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-01T00:00:00+08:00",
                    "2026-09-02T00:00:00+08:00",
                    "Not/AZone",
                    AggregationGranularity.DAY,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fixedOffsetZoneIsRejectedAsNonIana() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-01T00:00:00+08:00",
                    "2026-09-02T00:00:00+08:00",
                    "+08:00",
                    AggregationGranularity.DAY,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void spanBeyondGranularityLimitIsRejected() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-01T00:00:00Z",
                    "2026-09-02T01:00:00Z",
                    "UTC",
                    AggregationGranularity.FIVE_MINUTES,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownGranularityIsRejected() {
    assertThatThrownBy(
            () ->
                AggregationQueryContext.of(
                    "2026-09-01T00:00:00Z",
                    "2026-09-02T00:00:00Z",
                    "UTC",
                    null,
                    "p2-2026-09-22-a",
                    limits()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
