package com.lang.portal.web.dashboard;

public record DashboardCountMetric(
    Long value, String unit, DashboardAvailability availability, DashboardReasonCode reasonCode) {
  public DashboardCountMetric {
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("计数指标单位为必填");
    }
    if (availability == null || availability == DashboardAvailability.PARTIAL) {
      throw new IllegalArgumentException("计数指标可用性只允许 AVAILABLE 或 UNAVAILABLE");
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

  public static DashboardCountMetric available(long value, String unit) {
    return new DashboardCountMetric(value, unit, DashboardAvailability.AVAILABLE, null);
  }

  public static DashboardCountMetric unavailable(DashboardReasonCode reason) {
    return new DashboardCountMetric(null, defaultUnit(), DashboardAvailability.UNAVAILABLE, reason);
  }

  public static DashboardCountMetric unavailable(String unit, DashboardReasonCode reason) {
    return new DashboardCountMetric(null, unit, DashboardAvailability.UNAVAILABLE, reason);
  }

  private static String defaultUnit() {
    return "count";
  }
}
