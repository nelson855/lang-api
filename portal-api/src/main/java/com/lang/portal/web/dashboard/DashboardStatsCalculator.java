package com.lang.portal.web.dashboard;

import com.lang.portal.base.aggregation.AggregationAccumulator;
import com.lang.portal.base.aggregation.AggregationBaselinePolicy;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationMetric;
import com.lang.portal.base.aggregation.AggregationTotals;
import com.lang.portal.base.aggregation.FieldSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DashboardStatsCalculator {

  private DashboardStatsCalculator() {}

  public static DashboardStatsData calculate(
      List<AggregationLogRecord> records, String baselineVersion) {
    AggregationBaselinePolicy.requireSupported(baselineVersion);
    List<AggregationLogRecord> snapshot = records == null ? List.of() : List.copyOf(records);

    AggregationAccumulator accumulator = new AggregationAccumulator();
    snapshot.forEach(accumulator::add);
    AggregationTotals totals = accumulator.totals();

    DashboardCountMetric tokenUsage =
        DashboardCountMetric.available(
            Math.addExact(totals.inputTokens(), totals.outputTokens()), "tokens");
    DashboardCountMetric activeKeys = activeKeys(snapshot);
    DashboardAverageMetric averageLatency = averageLatency(snapshot, totals);
    DashboardCountMetric requestTotal =
        DashboardCountMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    DashboardMoneyMetric spend =
        DashboardMoneyMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    DashboardRatioMetric successRate =
        DashboardRatioMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED);

    if (AggregationBaselinePolicy.metricStatus(baselineVersion, AggregationMetric.TOKEN_USAGE)
        != FieldSupport.VERIFIED) {
      tokenUsage = DashboardCountMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED);
    }

    DashboardMetricsDto metrics =
        new DashboardMetricsDto(
            requestTotal, tokenUsage, spend, activeKeys, successRate, averageLatency);
    DashboardRequestTrendDto requestTrend =
        new DashboardRequestTrendDto(
            DashboardAvailability.UNAVAILABLE,
            DashboardReasonCode.BASELINE_NOT_VERIFIED,
            "requests",
            List.of());
    DashboardSpendTrendDto spendTrend =
        new DashboardSpendTrendDto(
            DashboardAvailability.UNAVAILABLE,
            DashboardReasonCode.BASELINE_NOT_VERIFIED,
            null,
            List.of());
    DashboardRecentRequestsDto recentRequests =
        new DashboardRecentRequestsDto(
            DashboardAvailability.PARTIAL,
            DashboardReasonCode.PARTIAL_SOURCE_COVERAGE,
            recentItems(snapshot));
    // baselineVersion 与 range 由查询服务装配，这里先占位，range 置空由上层覆盖
    return new DashboardStatsData(baselineVersion, null, metrics, requestTrend, spendTrend, recentRequests);
  }

  private static List<DashboardRecentRequestItemDto> recentItems(
      List<AggregationLogRecord> snapshot) {
    List<AggregationLogRecord> sorted = new ArrayList<>(snapshot);
    sorted.sort(
        Comparator.comparing(
                AggregationLogRecord::occurredAt, Comparator.reverseOrder())
            .thenComparing(
                r -> r.requestId() == null || r.requestId().isBlank() ? null : r.requestId(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                AggregationLogRecord::tokenId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                r -> r.model() == null || r.model().isBlank() ? null : r.model(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(r -> r.inputTokens() + r.outputTokens())
            .thenComparing(AggregationLogRecord::durationMs));
    return sorted.stream()
        .limit(10)
        .map(
            r ->
                new DashboardRecentRequestItemDto(
                    r.occurredAt().toString(),
                    r.requestId(),
                    r.keyName(),
                    r.model(),
                    "SUCCESS",
                    r.inputTokens(),
                    r.outputTokens(),
                    r.durationMs(),
                    r.stream()))
        .toList();
  }

  private static DashboardCountMetric activeKeys(List<AggregationLogRecord> snapshot) {
    if (snapshot.isEmpty()) {
      return DashboardCountMetric.available(0L, "keys");
    }
    Set<Long> distinct = new HashSet<>();
    for (AggregationLogRecord record : snapshot) {
      if (record.tokenId() == null) {
        return DashboardCountMetric.unavailable(
            "keys", DashboardReasonCode.SOURCE_FIELD_MISSING);
      }
      distinct.add(record.tokenId());
    }
    return DashboardCountMetric.available(distinct.size(), "keys");
  }

  private static DashboardAverageMetric averageLatency(
      List<AggregationLogRecord> snapshot, AggregationTotals totals) {
    if (snapshot.isEmpty()) {
      return DashboardAverageMetric.unavailable(DashboardReasonCode.NO_DATA);
    }
    BigDecimal average =
        BigDecimal.valueOf(totals.durationMs())
            .divide(BigDecimal.valueOf(snapshot.size()), 3, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    return DashboardAverageMetric.available(average, "ms");
  }
}
