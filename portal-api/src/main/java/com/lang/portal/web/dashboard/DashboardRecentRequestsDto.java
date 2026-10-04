package com.lang.portal.web.dashboard;

import java.util.List;

public record DashboardRecentRequestsDto(
    DashboardAvailability availability,
    DashboardReasonCode reasonCode,
    List<DashboardRecentRequestItemDto> items) {}
