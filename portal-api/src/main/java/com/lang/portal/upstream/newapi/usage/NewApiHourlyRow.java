package com.lang.portal.upstream.newapi.usage;

public record NewApiHourlyRow(
    long hourEpochSecond, String model, long requestCount, long tokenCount, long quota) {}
