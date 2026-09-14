package com.lang.portal.web.requestlog;

public record RequestLogDto(
    String occurredAt,
    String requestId,
    String keyName,
    String model,
    String result,
    long inputTokens,
    long outputTokens,
    long durationMs,
    boolean stream,
    String quota,
    String amount,
    String currency,
    String protocol,
    Long firstTokenLatencyMs) {}
