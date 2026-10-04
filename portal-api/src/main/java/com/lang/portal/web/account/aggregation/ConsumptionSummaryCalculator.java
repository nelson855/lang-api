package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationQueryContext;
import java.util.Objects;

public final class ConsumptionSummaryCalculator {

  private ConsumptionSummaryCalculator() {}

  public static AccountConsumptionSummaryData summarize(
      ConsumptionSnapshot snapshot, AggregationQueryContext context) {
    Objects.requireNonNull(snapshot, "消费快照不能为空");
    Objects.requireNonNull(context, "查询上下文不能为空");

    AccountConsumptionRangeDto range =
        new AccountConsumptionRangeDto(
            context.start().toString(),
            context.end().toString(),
            context.zone().getId(),
            context.granularity().name());

    AccountConsumptionMetric recordCount =
        AccountConsumptionMetric.available(Integer.toString(snapshot.totalCount()), "records");
    AccountConsumptionMetric quotaTotal =
        AccountConsumptionMetric.available(snapshot.totalQuota().toString(), "quota");
    AccountMoneyMetric moneyTotal =
        AccountMoneyMetric.unavailable(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);

    AccountSourceCoverageDto coverage =
        snapshot.missingReferenceCount() == 0
            ? new AccountSourceCoverageDto(
                AccountTransactionType.CONSUMPTION, AccountAvailability.AVAILABLE, null)
            : new AccountSourceCoverageDto(
                AccountTransactionType.CONSUMPTION,
                AccountAvailability.PARTIAL,
                AccountReasonCode.MISSING_STABLE_REFERENCE);

    return new AccountConsumptionSummaryData(
        context.baselineVersion(), range, recordCount, quotaTotal, moneyTotal, coverage);
  }
}
