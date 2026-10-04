package com.lang.portal.web.dashboard;

public record DashboardStatsData(
    String baselineVersion,
    DashboardRangeDto range,
    DashboardMetricsDto metrics,
    DashboardRequestTrendDto requestTrend,
    DashboardSpendTrendDto spendTrend,
    DashboardRecentRequestsDto recentRequests) {}
