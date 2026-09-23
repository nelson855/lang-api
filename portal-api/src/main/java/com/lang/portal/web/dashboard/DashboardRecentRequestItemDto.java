package com.lang.portal.web.dashboard;

public record DashboardRecentRequestItemDto(
    String occurredAt,
    String requestId,
    String keyName,
    String model,
    String outcome,
    long inputTokens,
    long outputTokens,
    long durationMs,
    boolean stream) {}
