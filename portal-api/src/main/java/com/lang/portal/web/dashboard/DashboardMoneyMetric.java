package com.lang.portal.web.dashboard;

public record DashboardMoneyMetric(
    String value, String currency, DashboardAvailability availability, DashboardReasonCode reasonCode) {
  public DashboardMoneyMetric {
    if (availability == null || availability == DashboardAvailability.PARTIAL) {
      throw new IllegalArgumentException("金额指标可用性只允许 AVAILABLE 或 UNAVAILABLE");
    }
    if (availability == DashboardAvailability.AVAILABLE) {
      if (value == null || currency == null || currency.isBlank() || reasonCode != null) {
        throw new IllegalArgumentException("可用金额必须有值、有币种且无原因");
      }
    } else {
      if (value != null || reasonCode == null) {
        throw new IllegalArgumentException("不可用金额必须无值且有原因");
      }
    }
  }

  public static DashboardMoneyMetric available(String value, String currency) {
    return new DashboardMoneyMetric(value, currency, DashboardAvailability.AVAILABLE, null);
  }

  public static DashboardMoneyMetric unavailable(DashboardReasonCode reason) {
    return new DashboardMoneyMetric(null, null, DashboardAvailability.UNAVAILABLE, reason);
  }
}
