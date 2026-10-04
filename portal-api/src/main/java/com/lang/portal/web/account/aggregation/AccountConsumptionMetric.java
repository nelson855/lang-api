package com.lang.portal.web.account.aggregation;

public record AccountConsumptionMetric(
    String value, String unit, AccountAvailability availability, AccountReasonCode reasonCode) {
  public AccountConsumptionMetric {
    if (availability == null || availability == AccountAvailability.PARTIAL) {
      throw new IllegalArgumentException("指标可用性只允许 AVAILABLE 或 UNAVAILABLE");
    }
    if (availability == AccountAvailability.AVAILABLE) {
      if (value == null || value.isBlank() || unit == null || unit.isBlank() || reasonCode != null) {
        throw new IllegalArgumentException("可用指标必须有值、有单位且无原因");
      }
    } else {
      if (value != null || unit != null || reasonCode == null) {
        throw new IllegalArgumentException("不可用指标必须无值且无单位并携带原因");
      }
    }
  }

  public static AccountConsumptionMetric available(String value, String unit) {
    return new AccountConsumptionMetric(value, unit, AccountAvailability.AVAILABLE, null);
  }

  public static AccountConsumptionMetric unavailable(AccountReasonCode reason) {
    if (reason == null) {
      throw new IllegalArgumentException("不可用指标必须提供原因");
    }
    return new AccountConsumptionMetric(null, null, AccountAvailability.UNAVAILABLE, reason);
  }
}
