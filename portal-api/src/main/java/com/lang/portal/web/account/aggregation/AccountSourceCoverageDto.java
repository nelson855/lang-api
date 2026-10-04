package com.lang.portal.web.account.aggregation;

public record AccountSourceCoverageDto(
    AccountTransactionType type, AccountAvailability availability, AccountReasonCode reasonCode) {
  public AccountSourceCoverageDto {
    if (type == null) {
      throw new IllegalArgumentException("来源覆盖必须指定类型");
    }
    if (availability == null) {
      throw new IllegalArgumentException("来源覆盖必须指定可用性");
    }
    if (availability == AccountAvailability.AVAILABLE && reasonCode != null) {
      throw new IllegalArgumentException("可用来源不允许携带原因");
    }
    if (availability != AccountAvailability.AVAILABLE && reasonCode == null) {
      throw new IllegalArgumentException("非可用来源必须携带原因");
    }
  }
}
