package com.lang.portal.web.account.aggregation;

import java.math.BigDecimal;

public record AccountTransactionItemDto(
    String transactionId,
    String occurredAt,
    AccountTransactionType type,
    AccountTransactionDirection direction,
    String amount,
    AccountTransactionUnit unit,
    String currency,
    AccountTransactionStatus status,
    String remark,
    String referenceId) {
  public AccountTransactionItemDto {
    if (transactionId == null || transactionId.isBlank()) {
      throw new IllegalArgumentException("流水项目必须包含稳定交易 ID");
    }
    if (occurredAt == null || occurredAt.isBlank()) {
      throw new IllegalArgumentException("流水项目必须包含发生时间");
    }
    if (type == null || direction == null || unit == null || status == null) {
      throw new IllegalArgumentException("流水项目必须包含类型、方向、单位与状态");
    }
    if (amount == null || amount.isBlank()) {
      throw new IllegalArgumentException("流水项目必须包含金额");
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(amount);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("金额必须为十进制字符串");
    }
    if (parsed.signum() < 0) {
      throw new IllegalArgumentException("金额必须为非负值，方向由 direction 表达");
    }
    if (unit == AccountTransactionUnit.QUOTA && currency != null) {
      throw new IllegalArgumentException("quota 单位不允许携带币种");
    }
    if (unit == AccountTransactionUnit.CURRENCY && (currency == null || currency.isBlank())) {
      throw new IllegalArgumentException("货币单位必须显式提供币种");
    }
    if (remark != null && remark.isBlank()) {
      remark = null;
    }
    if (referenceId != null && referenceId.isBlank()) {
      referenceId = null;
    }
  }
}
