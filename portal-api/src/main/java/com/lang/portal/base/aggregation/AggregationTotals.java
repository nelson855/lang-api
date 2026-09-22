package com.lang.portal.base.aggregation;

import java.math.BigDecimal;

public record AggregationTotals(long inputTokens, long outputTokens, long durationMs, BigDecimal rawQuota, long recordCount) {}
