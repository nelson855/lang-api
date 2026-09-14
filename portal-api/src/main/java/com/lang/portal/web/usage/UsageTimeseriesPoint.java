package com.lang.portal.web.usage;

public record UsageTimeseriesPoint(
    String bucketStart, long requestCount, long tokenCount, String quota, String amount) {}
