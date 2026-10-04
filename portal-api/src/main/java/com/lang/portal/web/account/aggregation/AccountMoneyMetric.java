package com.lang.portal.web.account.aggregation;

public record AccountMoneyMetric(
    String value, String currency, AccountAvailability availability, AccountReasonCode reasonCode) {
  public AccountMoneyMetric {
    if (availability == null || availability == AccountAvailability.PARTIAL) {
      throw new IllegalArgumentException("金额指标可用性只允许 AVAILABLE 或 UNAVAILABLE");
    }
    if (availability == AccountAvailability.AVAILABLE) {
      if (value == null || value.isBlank() || currency == null || currency.isBlank() || reasonCode != null) {
        throw new IllegalArgumentException("可用金额必须有值、有币种且无原因");
      }
    } else {
      if (value != null || currency != null || reasonCode == null) {
        throw new IllegalArgumentException("不可用金额必须无值且无币种并携带原因");
      }
    }
  }

  public static AccountMoneyMetric available(String value, String currency) {
    return new AccountMoneyMetric(value, currency, AccountAvailability.AVAILABLE, null);
  }

  public static AccountMoneyMetric unavailable(AccountReasonCode reason) {
    if (reason == null) {
      throw new IllegalArgumentException("不可用金额必须提供原因");
    }
    return new AccountMoneyMetric(null, null, AccountAvailability.UNAVAILABLE, reason);
  }
}
