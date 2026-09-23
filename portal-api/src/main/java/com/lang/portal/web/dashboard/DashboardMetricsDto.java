package com.lang.portal.web.dashboard;

public record DashboardMetricsDto(
    DashboardCountMetric requestTotal,
    DashboardCountMetric tokenUsage,
    DashboardMoneyMetric spend,
    DashboardCountMetric activeKeys,
    DashboardRatioMetric successRate,
    DashboardAverageMetric averageLatency) {}
