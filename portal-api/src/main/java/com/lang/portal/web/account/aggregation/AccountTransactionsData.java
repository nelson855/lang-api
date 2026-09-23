package com.lang.portal.web.account.aggregation;

import java.util.List;

public record AccountTransactionsData(
    String baselineVersion,
    AccountConsumptionRangeDto range,
    AccountTransactionType type,
    int page,
    int pageSize,
    long total,
    AccountAvailability availability,
    AccountReasonCode reasonCode,
    List<AccountSourceCoverageDto> coverage,
    List<AccountTransactionItemDto> items) {
  public AccountTransactionsData {
    if (baselineVersion == null || baselineVersion.isBlank()) {
      throw new IllegalArgumentException("流水响应必须携带口径版本");
    }
    if (range == null) {
      throw new IllegalArgumentException("流水响应必须携带规范化范围");
    }
    if (page < 1) {
      throw new IllegalArgumentException("页码必须从 1 开始");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("页大小必须为正");
    }
    if (total < 0) {
      throw new IllegalArgumentException("总数不得为负");
    }
    if (availability == null) {
      throw new IllegalArgumentException("流水响应必须包含总体可用性");
    }
    if (availability == AccountAvailability.AVAILABLE && reasonCode != null) {
      throw new IllegalArgumentException("可用流水不允许携带总体原因");
    }
    if (availability == AccountAvailability.UNAVAILABLE && reasonCode == null) {
      throw new IllegalArgumentException("不可用流水必须携带总体原因");
    }
    coverage = coverage == null ? List.of() : List.copyOf(coverage);
    items = items == null ? List.of() : List.copyOf(items);
  }
}
