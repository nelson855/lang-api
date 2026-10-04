package com.lang.portal.base.aggregation;

import java.time.Instant;

public record AggregationLogRecord(
    Instant occurredAt,
    AggregationLogResult result,
    Long tokenId,
    String model,
    String requestId,
    String keyName,
    long inputTokens,
    long outputTokens,
    long durationMs,
    boolean stream,
    long rawQuota) {
  public AggregationLogRecord {
    if (occurredAt == null || result == null) {
      throw new IllegalArgumentException("发生时间和日志结果均为必填");
    }
  }
}
