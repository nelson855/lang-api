package com.lang.portal.web.dashboard;

import java.util.List;

public record DashboardRequestTrendDto(
    DashboardAvailability availability,
    DashboardReasonCode reasonCode,
    String unit,
    List<DashboardTrendPointDto> points) {}
