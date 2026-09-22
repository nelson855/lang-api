package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregationAccumulatorTests {

  private static AggregationLogRecord record(long input, long output, long durationMs, long quota) {
    return new AggregationLogRecord(
        Instant.parse("2026-09-01T00:00:00Z"),
        AggregationLogResult.SUCCESS,
        9L,
        "gpt-test",
        "req-1",
        "probe-key-01",
        input,
        output,
        durationMs,
        false,
        quota);
  }

  @Test
  void sumsTokensDurationAndRawQuotaExactly() {
    AggregationAccumulator accumulator = new AggregationAccumulator();
    accumulator.add(record(10L, 20L, 3000L, 500L));
    accumulator.add(record(5L, 7L, 1000L, 300L));
    AggregationTotals totals = accumulator.totals();
    assertThat(totals.inputTokens()).isEqualTo(15L);
    assertThat(totals.outputTokens()).isEqualTo(27L);
    assertThat(totals.durationMs()).isEqualTo(4000L);
    assertThat(totals.rawQuota()).isEqualByComparingTo(new BigDecimal("800"));
    assertThat(totals.rawQuota().scale()).isEqualTo(0);
  }

  @Test
  void tokenOverflowFailsWithoutWrapping() {
    AggregationAccumulator accumulator = new AggregationAccumulator();
    accumulator.add(record(Long.MAX_VALUE, 0L, 0L, 0L));
    assertThatThrownBy(() -> accumulator.add(record(1L, 0L, 0L, 0L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("溢出");
  }

  @Test
  void durationOverflowFailsWithoutWrapping() {
    AggregationAccumulator accumulator = new AggregationAccumulator();
    accumulator.add(record(0L, 0L, Long.MAX_VALUE, 0L));
    assertThatThrownBy(() -> accumulator.add(record(0L, 0L, 1L, 0L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("溢出");
  }

  @Test
  void rawQuotaAccumulationNeverRounds() {
    AggregationAccumulator accumulator = new AggregationAccumulator();
    accumulator.add(record(0L, 0L, 0L, 1L));
    accumulator.add(record(0L, 0L, 0L, 2L));
    assertThat(accumulator.totals().rawQuota()).isEqualByComparingTo(new BigDecimal("3"));
  }
}
