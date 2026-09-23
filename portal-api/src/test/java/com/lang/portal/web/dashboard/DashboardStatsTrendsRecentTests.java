package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardStatsTrendsRecentTests {

  private static AggregationLogRecord record(
      String instant,
      String requestId,
      Long tokenId,
      String keyName,
      String model,
      long input,
      long output,
      long durationMs) {
    return new AggregationLogRecord(
        Instant.parse(instant),
        AggregationLogResult.SUCCESS,
        tokenId,
        model,
        requestId,
        keyName,
        input,
        output,
        durationMs,
        false,
        500L);
  }

  @Test
  void currentBaselineTrendsStayUnavailableWithEmptyPoints() {
    List<AggregationLogRecord> records =
        List.of(record("2026-09-01T00:01:40Z", "req-1", 9L, "probe-key-01", "gpt-test", 10L, 20L, 3000L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.requestTrend().availability()).isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.requestTrend().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.requestTrend().points()).isEmpty();
    assertThat(data.spendTrend().availability()).isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.spendTrend().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.spendTrend().points()).isEmpty();
    assertThat(data.spendTrend().currency()).isNull();
  }

  @Test
  void recentRequestsSortStableTruncateToTenAndKeepNullableFields() {
    List<AggregationLogRecord> records = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      records.add(
          record(
              "2026-09-01T00:01:40Z",
              String.format("req-%02d", i),
              9L,
              "probe-key-01",
              "gpt-test",
              10L,
              20L,
              1000L + i));
    }
    // 更晚的一条应排第一
    records.add(
        record("2026-09-01T01:05:00Z", "req-late", 10L, "probe-key-01", "gpt-test", 1L, 1L, 500L));
    // 缺可空展示字段仍保留
    records.add(
        new AggregationLogRecord(
            Instant.parse("2026-09-01T00:02:40Z"),
            AggregationLogResult.SUCCESS,
            11L,
            null,
            null,
            null,
            3L,
            4L,
            700L,
            true,
            100L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.recentRequests().availability()).isEqualTo(DashboardAvailability.PARTIAL);
    assertThat(data.recentRequests().reasonCode())
        .isEqualTo(DashboardReasonCode.PARTIAL_SOURCE_COVERAGE);
    assertThat(data.recentRequests().items()).hasSize(10);
    assertThat(data.recentRequests().items().get(0).requestId()).isEqualTo("req-late");
    assertThat(data.recentRequests().items())
        .allMatch(item -> "SUCCESS".equals(item.outcome()));
    // 稳定并列排序：同一时刻按 requestId 字典序
    assertThat(data.recentRequests().items().get(2).requestId()).isEqualTo("req-00");
    assertThat(data.recentRequests().items().get(3).requestId()).isEqualTo("req-01");
  }

  @Test
  void recentRequestsKeepNullableFieldsWithoutDropping() {
    List<AggregationLogRecord> records =
        List.of(
            new AggregationLogRecord(
                Instant.parse("2026-09-01T00:02:40Z"),
                AggregationLogResult.SUCCESS,
                11L,
                null,
                null,
                null,
                3L,
                4L,
                700L,
                true,
                100L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.recentRequests().items()).hasSize(1);
    DashboardRecentRequestItemDto only = data.recentRequests().items().get(0);
    assertThat(only.requestId()).isNull();
    assertThat(only.keyName()).isNull();
    assertThat(only.model()).isNull();
    assertThat(only.outcome()).isEqualTo("SUCCESS");
  }
}
