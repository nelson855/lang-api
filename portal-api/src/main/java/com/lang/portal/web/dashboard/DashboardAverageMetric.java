package com.lang.portal.web.dashboard;

import java.math.BigDecimal;

public record DashboardAverageMetric(
    BigDecimal value, String unit, DashboardAvailability availability, DashboardReasonCode reasonCode) {
  public DashboardAverageMetric {
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("平均值指标单位为必填");
    }
    if (availability == null || availability == DashboardAvailability.PARTIAL) {
      throw new IllegalArgumentException("平均值指标可用性只允许 AVAILABLE 或 UNAVAILABLE");
    }
    if (availability == DashboardAvailability.AVAILABLE) {
      if (value == null || reasonCode != null) {
        throw new IllegalArgumentException("可用指标必须有值且无原因");
      }
    } else {
      if (value != null || reasonCode == null) {
        throw new IllegalArgumentException("不可用指标必须无值且有原因");
      }
    }
  }

  public static DashboardAverageMetric available(BigDecimal value, String unit) {
    return new DashboardAverageMetric(value, unit, DashboardAvailability.AVAILABLE, null);
  }

  public static DashboardAverageMetric unavailable(DashboardReasonCode reason) {
    return new DashboardAverageMetric(null, "ms", DashboardAvailability.UNAVAILABLE, reason);
  }
}
