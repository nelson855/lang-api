package com.lang.portal.web.account.aggregation;

public record AccountConsumptionSummaryData(
    String baselineVersion,
    AccountConsumptionRangeDto range,
    AccountConsumptionMetric recordCount,
    AccountConsumptionMetric quotaTotal,
    AccountMoneyMetric moneyTotal,
    AccountSourceCoverageDto coverage) {
  public AccountConsumptionSummaryData {
    if (baselineVersion == null || baselineVersion.isBlank()) {
      throw new IllegalArgumentException("汇总响应必须携带口径版本");
    }
    if (range == null || recordCount == null || quotaTotal == null || moneyTotal == null || coverage == null) {
      throw new IllegalArgumentException("汇总响应必须包含范围、指标与来源覆盖");
    }
  }
}
