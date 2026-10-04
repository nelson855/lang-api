package com.lang.portal.base.aggregation;

import java.math.BigDecimal;

public final class AggregationAccumulator {

  private long inputTokens;
  private long outputTokens;
  private long durationMs;
  private BigDecimal rawQuota = BigDecimal.ZERO;
  private long recordCount;

  public void add(AggregationLogRecord record) {
    try {
      inputTokens = Math.addExact(inputTokens, record.inputTokens());
      outputTokens = Math.addExact(outputTokens, record.outputTokens());
      durationMs = Math.addExact(durationMs, record.durationMs());
    } catch (ArithmeticException e) {
      throw new IllegalStateException("聚合累计溢出，拒绝返回截断结果", e);
    }
    rawQuota = rawQuota.add(BigDecimal.valueOf(record.rawQuota()));
    recordCount++;
  }

  public AggregationTotals totals() {
    return new AggregationTotals(inputTokens, outputTokens, durationMs, rawQuota, recordCount);
  }
}
