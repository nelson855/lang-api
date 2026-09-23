package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardStatsBaselineTests {

  @Test
  void currentBaselineKeepsThreeMetricsUnavailableWithoutMoneyConversion() {
    List<AggregationLogRecord> records =
        List.of(
            new AggregationLogRecord(
                Instant.parse("2026-09-01T00:01:40Z"),
                AggregationLogResult.SUCCESS,
                9L,
                "gpt-test",
                "req-1",
                "probe-key-01",
                10L,
                20L,
                3000L,
                false,
                500L));

    DashboardStatsData data =
        DashboardStatsCalculator.calculate(records, "p2-2026-09-22-a");

    assertThat(data.metrics().requestTotal().availability())
        .isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.metrics().requestTotal().reasonCode())
        .isEqualTo(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.metrics().spend().availability())
        .isEqualTo(DashboardAvailability.UNAVAILABLE);
    assertThat(data.metrics().successRate().availability())
        .isEqualTo(DashboardAvailability.UNAVAILABLE);
    // 已验证三项仍可用，证明没有把 type=2 数量挪作 requestTotal
    assertThat(data.metrics().tokenUsage().value()).isEqualTo(30L);
    assertThat(data.metrics().activeKeys().value()).isEqualTo(1L);
  }

  @Test
  void identicalRecordsAndEmptyListStayStable() {
    AggregationLogRecord same =
        new AggregationLogRecord(
            Instant.parse("2026-09-01T00:01:40Z"),
            AggregationLogResult.SUCCESS,
            9L,
            "gpt-test",
            "req-same",
            "probe-key-01",
            10L,
            20L,
            3000L,
            false,
            500L);
    DashboardStatsData duplicated =
        DashboardStatsCalculator.calculate(List.of(same, same), "p2-2026-09-22-a");
    assertThat(duplicated.recentRequests().items()).hasSize(2);
    assertThat(duplicated.recentRequests().items().get(0).requestId())
        .isEqualTo(duplicated.recentRequests().items().get(1).requestId());

    DashboardStatsData empty =
        DashboardStatsCalculator.calculate(List.of(), "p2-2026-09-22-a");
    assertThat(empty.recentRequests().availability()).isEqualTo(DashboardAvailability.PARTIAL);
    assertThat(empty.recentRequests().reasonCode())
        .isEqualTo(DashboardReasonCode.PARTIAL_SOURCE_COVERAGE);
    assertThat(empty.recentRequests().items()).isEmpty();
  }
}
