package com.lang.portal.web.dashboard;

import java.util.List;

public record DashboardSpendTrendDto(
    DashboardAvailability availability,
    DashboardReasonCode reasonCode,
    String currency,
    List<DashboardTrendPointDto> points) {}
