package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardStatsDtoTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void responseContractFreezesFieldNamesAndTypes() throws Exception {
    DashboardStatsData data =
        new DashboardStatsData(
            "p2-2026-09-22-a",
            new DashboardRangeDto(
                "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR"),
            new DashboardMetricsDto(
                DashboardCountMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardCountMetric.available(90L, "tokens"),
                DashboardMoneyMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardCountMetric.available(2L, "keys"),
                DashboardRatioMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardAverageMetric.unavailable(DashboardReasonCode.NO_DATA)),
            new DashboardRequestTrendDto(
                DashboardAvailability.UNAVAILABLE,
                DashboardReasonCode.BASELINE_NOT_VERIFIED,
                "requests",
                List.of()),
            new DashboardSpendTrendDto(
                DashboardAvailability.UNAVAILABLE,
                DashboardReasonCode.BASELINE_NOT_VERIFIED,
                null,
                List.of()),
            new DashboardRecentRequestsDto(
                DashboardAvailability.PARTIAL,
                DashboardReasonCode.PARTIAL_SOURCE_COVERAGE,
                List.of()));

    Map<?, ?> json = mapper.convertValue(data, Map.class);

    assertThat(json.keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder(
            "baselineVersion", "range", "metrics", "requestTrend", "spendTrend", "recentRequests");
    assertThat(((Map<?, ?>) json.get("range")).keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("startTime", "endTime", "timezone", "granularity");
    assertThat(((Map<?, ?>) json.get("metrics")).keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder(
            "requestTotal", "tokenUsage", "spend", "activeKeys", "successRate", "averageLatency");
  }
}
