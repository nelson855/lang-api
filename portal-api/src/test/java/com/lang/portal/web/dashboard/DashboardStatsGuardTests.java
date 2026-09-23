package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardStatsGuardTests {

  private final ObjectMapper mapper = new ObjectMapper();

  private DashboardStatsData sample() {
    return new DashboardStatsData(
        "p2-2026-09-22-a",
        new DashboardRangeDto("2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR"),
        new DashboardMetricsDto(
            DashboardCountMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
            DashboardCountMetric.available(90L, "tokens"),
            DashboardMoneyMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
            DashboardCountMetric.available(2L, "keys"),
            DashboardRatioMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
            DashboardAverageMetric.available(new BigDecimal("2000.000"), "ms")),
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
            List.of(
                new DashboardRecentRequestItemDto(
                    "2026-09-01T00:01:40Z",
                    "req-1",
                    "probe-key-01",
                    "gpt-test",
                    "SUCCESS",
                    10L,
                    20L,
                    3000L,
                    false))));
  }

  @Test
  void dashboardJsonContainsNoSensitiveFields() throws Exception {
    String json = mapper.writeValueAsString(sample());

    assertThat(json)
        .doesNotContain(
            "tokenId",
            "token_id",
            "sk-",
            "rawQuota",
            "quotaPerUsd",
            "channel",
            "vendor",
            "supplier",
            "rawResponse",
            "raw_response",
            "upstream-session");

    Map<?, ?> root = mapper.readValue(json, Map.class);
    assertThat(root.keySet().stream().map(Object::toString).toList())
        .doesNotContain("success", "message", "data", "items");
    Map<?, ?> recent = (Map<?, ?>) root.get("recentRequests");
    List<?> items = (List<?>) recent.get("items");
    Map<?, ?> first = (Map<?, ?>) items.get(0);
    assertThat(first.keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder(
            "occurredAt",
            "requestId",
            "keyName",
            "model",
            "outcome",
            "inputTokens",
            "outputTokens",
            "durationMs",
            "stream");
  }

  @Test
  void metricRejectsContradictoryStates() {
    assertThatThrownBy(() -> new DashboardCountMetric(null, "tokens", DashboardAvailability.AVAILABLE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DashboardCountMetric(
                    1L, "tokens", DashboardAvailability.AVAILABLE, DashboardReasonCode.NO_DATA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DashboardCountMetric(
                    1L, "tokens", DashboardAvailability.UNAVAILABLE, DashboardReasonCode.NO_DATA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DashboardCountMetric(
                    null, "tokens", DashboardAvailability.UNAVAILABLE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DashboardCountMetric(
                    1L, "tokens", DashboardAvailability.PARTIAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
