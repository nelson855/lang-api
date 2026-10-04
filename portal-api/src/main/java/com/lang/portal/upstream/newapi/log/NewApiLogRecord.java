package com.lang.portal.upstream.newapi.log;

import java.time.Instant;
import java.util.List;

public record NewApiLogRecord(
    Instant occurredAt,
    String keyName,
    String model,
    NewApiLogResult result,
    long inputTokens,
    long outputTokens,
    long durationMs,
    boolean stream,
    long quota,
    String requestId,
    Long tokenId) {

  public NewApiLogRecord {
    if (occurredAt == null) {
      throw new IllegalArgumentException("时间不能为空");
    }
  }
}
