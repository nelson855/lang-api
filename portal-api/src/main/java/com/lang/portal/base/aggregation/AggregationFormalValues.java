package com.lang.portal.base.aggregation;

import java.math.BigDecimal;
import java.util.Optional;

public final class AggregationFormalValues {

  private AggregationFormalValues() {}

  public static Optional<String> formalUsdAmount(BigDecimal rawQuota, String baselineVersion, long quotaPerUsd) {
    if (AggregationBaselinePolicy.metricStatus(baselineVersion, AggregationMetric.USD_AMOUNT)
        != FieldSupport.VERIFIED) {
      return Optional.empty();
    }
    return Optional.of(com.lang.portal.base.money.QuotaMoneyConverter.toUsd(rawQuota.longValueExact(), quotaPerUsd));
  }

  public static Optional<BigDecimal> formalSuccessRate(long successCount, long totalCount, String baselineVersion) {
    if (AggregationBaselinePolicy.metricStatus(baselineVersion, AggregationMetric.SUCCESS_RATE)
        != FieldSupport.VERIFIED) {
      return Optional.empty();
    }
    if (totalCount <= 0) {
      return Optional.empty();
    }
    return Optional.of(BigDecimal.valueOf(successCount).divide(BigDecimal.valueOf(totalCount)));
  }
}
