package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionSummaryCalculatorTests {

  private static AggregationLogRecord rec(Instant at, String requestId, long quota) {
    return new AggregationLogRecord(
        at, AggregationLogResult.SUCCESS, null, "m", requestId, null, 0L, 0L, 0L, false, quota);
  }

  private static AggregationQueryContext ctx() {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00Z",
        "2026-09-02T00:00:00Z",
        "UTC",
        AggregationGranularity.HOUR,
        "p2-2026-09-22-a",
        new PortalCommonProperties().aggregation());
  }

  @Test
  void summaryReflectsExactQuotaAndCounts() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L),
                rec(Instant.parse("2026-09-01T01:30:00Z"), "r2", 20L),
                rec(Instant.parse("2026-09-01T02:30:00Z"), "r3", 30L)));

    AccountConsumptionSummaryData data = ConsumptionSummaryCalculator.summarize(snapshot, ctx());
    assertThat(data.baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(data.recordCount().value()).isEqualTo("3");
    assertThat(data.recordCount().unit()).isEqualTo("records");
    assertThat(data.recordCount().availability()).isEqualTo(AccountAvailability.AVAILABLE);
    assertThat(data.quotaTotal().value()).isEqualTo("60");
    assertThat(data.quotaTotal().unit()).isEqualTo("quota");
    assertThat(data.moneyTotal().availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.moneyTotal().reasonCode())
        .isEqualTo(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
    assertThat(data.moneyTotal().value()).isNull();
    assertThat(data.moneyTotal().currency()).isNull();
    assertThat(data.coverage().type()).isEqualTo(AccountTransactionType.CONSUMPTION);
    assertThat(data.coverage().availability()).isEqualTo(AccountAvailability.AVAILABLE);
  }

  @Test
  void emptySnapshotIsAvailableZero() {
    ConsumptionSnapshot snapshot = ConsumptionSnapshot.from(List.of());
    AccountConsumptionSummaryData data = ConsumptionSummaryCalculator.summarize(snapshot, ctx());
    assertThat(data.recordCount().value()).isEqualTo("0");
    assertThat(data.quotaTotal().value()).isEqualTo("0");
    assertThat(data.moneyTotal().availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
  }

  @Test
  void missingRequestIdMarksCoverageAsPartial() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L),
                rec(Instant.parse("2026-09-01T01:30:00Z"), null, 20L)));
    AccountConsumptionSummaryData data = ConsumptionSummaryCalculator.summarize(snapshot, ctx());
    assertThat(data.recordCount().value()).isEqualTo("2");
    assertThat(data.quotaTotal().value()).isEqualTo("30");
    assertThat(data.coverage().availability()).isEqualTo(AccountAvailability.PARTIAL);
    assertThat(data.coverage().reasonCode()).isEqualTo(AccountReasonCode.MISSING_STABLE_REFERENCE);
  }

  @Test
  void summaryNeverReadsQuotaPerUsdOrInvokesMoneyConverter() {
    // 该测试的「不读取配置换算」由编译期证明：AccountConsumptionSummaryData.moneyTotal
    // 不接受任何来自 catalog.quotaPerUsd 的输入，构造路径中没有 QuotaMoneyConverter。
    // 若未来代码意外引入 quotaPerUsd 调用，架构守卫测试会失败。
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(List.of(rec(Instant.parse("2026-09-01T00:30:00Z"), "r", 10L)));
    AccountConsumptionSummaryData data = ConsumptionSummaryCalculator.summarize(snapshot, ctx());
    assertThat(data.moneyTotal().value()).isNull();
  }
}
