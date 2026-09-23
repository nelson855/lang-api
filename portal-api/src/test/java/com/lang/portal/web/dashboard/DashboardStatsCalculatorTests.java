package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardStatsCalculatorTests {

  private static AggregationLogRecord record(
      String instant, Long tokenId, long input, long output, long durationMs) {
    return new AggregationLogRecord(
        Instant.parse(instant),
        AggregationLogResult.SUCCESS,
        tokenId,
        "gpt-test",
        "req-" + instant + "-" + tokenId,
        "probe-key-01",
        input,
        output,
        durationMs,
        false,
        500L);
  }

  @Test
  void verifiedMetricsSumTokensDedupKeysAndAverageLatency() {
    List<AggregationLogRecord> records =
        List.of(
            record("2026-09-01T00:01:40Z", 9L, 10L, 20L, 3000L),
            record("2026-09-01T00:03:20Z", 9L, 5L, 5L, 2000L),
            record("2026-09-01T01:05:00Z", 10L, 7L, 8L, 1000L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.metrics().tokenUsage().availability())
        .isEqualTo(DashboardAvailability.AVAILABLE);
    assertThat(data.metrics().tokenUsage().value()).isEqualTo(55L);
    assertThat(data.metrics().activeKeys().value()).isEqualTo(2L);
    assertThat(data.metrics().averageLatency().availability())
        .isEqualTo(DashboardAvailability.AVAILABLE);
    assertThat(data.metrics().averageLatency().value())
        .isEqualByComparingTo(new BigDecimal("2000"));
  }

  @Test
  void emptyRecordsReturnZeroForAdditiveAndNoDataForLatency() {
    DashboardStatsData data =
        DashboardStatsCalculator.calculate(List.of(), "p2-2026-09-22-a");

    assertThat(data.metrics().tokenUsage().availability())
        .isEqualTo(DashboardAvailability.AVAILABLE);
    assertThat(data.metrics().tokenUsage().value()).isEqualTo(0L);
    assertThat(data.metrics().activeKeys().value()).isEqualTo(0L);
    assertThat(data.metrics().averageLatency().availability())
        .isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.metrics().averageLatency().reasonCode())
        .isEqualTo(DashboardReasonCode.NO_DATA);
    assertThat(data.metrics().requestTotal().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.metrics().spend().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.metrics().successRate().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
  }

  @Test
  void missingTokenIdMakesActiveKeysUnavailableOnly() {
    List<AggregationLogRecord> records =
        List.of(
            record("2026-09-01T00:01:40Z", 9L, 10L, 20L, 3000L),
            record("2026-09-01T00:03:20Z", null, 5L, 5L, 2000L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.metrics().activeKeys().availability())
        .isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.metrics().activeKeys().reasonCode())
        .isEqualTo(DashboardReasonCode.SOURCE_FIELD_MISSING);
    assertThat(data.metrics().tokenUsage().value()).isEqualTo(40L);
    assertThat(data.metrics().averageLatency().value())
        .isEqualByComparingTo(new BigDecimal("2500"));
  }

  @Test
  void averageLatencyRoundsToThreeDecimalsHalfUp() {
    List<AggregationLogRecord> records =
        List.of(
            record("2026-09-01T00:01:40Z", 9L, 1L, 1L, 1L),
            record("2026-09-01T00:03:20Z", 10L, 1L, 1L, 1L),
            record("2026-09-01T01:05:00Z", 11L, 1L, 1L, 1L));

    // 3ms / 3 = 1ms 整除对照
    assertThat(
            DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a")
                .metrics()
                .averageLatency()
                .value())
        .isEqualByComparingTo(new BigDecimal("1"));

    List<AggregationLogRecord> uneven =
        List.of(
            record("2026-09-01T00:01:40Z", 9L, 1L, 1L, 1L),
            record("2026-09-01T00:03:20Z", 10L, 1L, 1L, 1L),
            record("2026-09-01T01:05:00Z", 11L, 1L, 1L, 0L));
    // 2ms / 3 = 0.667
    assertThat(
            DashboardStatsCalculator.calculate(uneven, "p2-2026-09-22-a")
                .metrics()
                .averageLatency()
                .value())
        .isEqualByComparingTo(new BigDecimal("0.667"));
  }

  @Test
  void accumulationOverflowFailsWholeCalculation() {
    List<AggregationLogRecord> records =
        List.of(
            new AggregationLogRecord(
                Instant.parse("2026-09-01T00:01:40Z"),
                AggregationLogResult.SUCCESS,
                9L,
                "gpt-test",
                "req-1",
                "probe-key-01",
                Long.MAX_VALUE,
                1L,
                1L,
                false,
                0L),
            record("2026-09-01T00:03:20Z", 10L, 1L, 1L, 1L));

    assertThatThrownBy(
            () -> DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a"))
        .isInstanceOf(IllegalStateException.class);
  }
}
