package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConsumptionSnapshot {

  private final List<AggregationLogRecord> records;
  private final List<AggregationLogRecord> projectable;
  private final int missingReferenceCount;
  private final BigInteger totalQuota;

  private ConsumptionSnapshot(
      List<AggregationLogRecord> records,
      List<AggregationLogRecord> projectable,
      int missingReferenceCount,
      BigInteger totalQuota) {
    this.records = records;
    this.projectable = projectable;
    this.missingReferenceCount = missingReferenceCount;
    this.totalQuota = totalQuota;
  }

  public static ConsumptionSnapshot from(List<AggregationLogRecord> source) {
    Objects.requireNonNull(source, "消费日志来源不能为空");
    List<AggregationLogRecord> records = List.copyOf(source);
    List<AggregationLogRecord> projectable = new ArrayList<>(records.size());
    int missing = 0;
    BigInteger total = BigInteger.ZERO;
    for (AggregationLogRecord record : records) {
      if (record == null) {
        throw new IllegalArgumentException("消费日志记录不能为空");
      }
      if (record.rawQuota() < 0) {
        throw new IllegalArgumentException("消费日志原始 quota 不允许为负数");
      }
      total = total.add(BigInteger.valueOf(record.rawQuota()));
      if (record.requestId() == null || record.requestId().isBlank()) {
        missing++;
      } else {
        projectable.add(record);
      }
    }
    if (total.bitLength() > 63) {
      throw new IllegalStateException("消费 quota 累计超出可表达范围");
    }
    return new ConsumptionSnapshot(records, List.copyOf(projectable), missing, total);
  }

  public List<AggregationLogRecord> records() {
    return records;
  }

  public List<AggregationLogRecord> projectable() {
    return projectable;
  }

  public int totalCount() {
    return records.size();
  }

  public int missingReferenceCount() {
    return missingReferenceCount;
  }

  public BigInteger totalQuota() {
    return totalQuota;
  }
}
