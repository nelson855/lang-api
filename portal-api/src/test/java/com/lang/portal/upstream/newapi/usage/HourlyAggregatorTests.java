package com.lang.portal.upstream.newapi.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HourlyAggregatorTests {

  private static final Instant RANGE_START = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant RANGE_END = Instant.parse("2026-09-10T12:00:00Z");

  @Test
  void mergesSameHourAcrossModels() {
    List<NewApiHourlyRow> rows = List.of(
        new NewApiHourlyRow(1789030800L, "a-model", 5L, 100L, 500L),
        new NewApiHourlyRow(1789030800L, "b-model", 3L, 50L, 200L));

    List<HourlyBucket> buckets = HourlyAggregator.aggregate(rows, RANGE_START, RANGE_END);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).requestCount()).isEqualTo(8L);
    assertThat(buckets.get(0).tokenCount()).isEqualTo(150L);
    assertThat(buckets.get(0).quota()).isEqualTo(700L);
  }

  @Test
  void sortsAscendingAndKeepsEmptyWithoutFilling() {
    List<NewApiHourlyRow> rows = List.of(
        new NewApiHourlyRow(1789034400L, "a", 1L, 10L, 100L),
        new NewApiHourlyRow(1789030800L, "a", 2L, 20L, 200L));

    List<HourlyBucket> buckets = HourlyAggregator.aggregate(rows, RANGE_START, RANGE_END);

    assertThat(buckets).hasSize(2);
    assertThat(buckets.get(0).bucketStart()).isEqualTo(Instant.parse("2026-09-10T09:00:00Z"));
    assertThat(buckets.get(1).bucketStart()).isEqualTo(Instant.parse("2026-09-10T10:00:00Z"));
  }

  @Test
  void emptyResultStaysEmpty() {
    assertThat(HourlyAggregator.aggregate(List.of(), RANGE_START, RANGE_END)).isEmpty();
  }

  @Test
  void nonHourAlignedOrNegativeFailsWholeBatch() {
    List<NewApiHourlyRow> skewed =
        List.of(new NewApiHourlyRow(1789030801L, "a", 1L, 10L, 100L));
    assertThatThrownBy(() -> HourlyAggregator.aggregate(skewed, RANGE_START, RANGE_END))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);

    List<NewApiHourlyRow> negative =
        List.of(new NewApiHourlyRow(1789030800L, "a", -1L, 10L, 100L));
    assertThatThrownBy(() -> HourlyAggregator.aggregate(negative, RANGE_START, RANGE_END))
        .isInstanceOf(UpstreamException.class);

    List<NewApiHourlyRow> outOfRange =
        List.of(new NewApiHourlyRow(1789027200L, "a", 1L, 10L, 100L));
    assertThatThrownBy(() -> HourlyAggregator.aggregate(outOfRange, RANGE_START, RANGE_END))
        .isInstanceOf(UpstreamException.class);
  }
}
