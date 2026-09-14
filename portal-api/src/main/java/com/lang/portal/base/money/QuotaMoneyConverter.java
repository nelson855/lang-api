package com.lang.portal.base.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class QuotaMoneyConverter {
  private QuotaMoneyConverter() {}

  public static String toUsd(long quota, long quotaPerUsd) {
    if (quotaPerUsd <= 0) {
      throw new IllegalStateException("非法 quota-per-usd 配置");
    }
    if (quota < 0) {
      throw new IllegalArgumentException("quota 必须非负");
    }
    BigDecimal amount = BigDecimal.valueOf(quota)
        .divide(BigDecimal.valueOf(quotaPerUsd), 10, RoundingMode.HALF_UP);
    return format(amount);
  }

  private static String format(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() < 1) {
      stripped = stripped.setScale(1);
    } else if (stripped.scale() > 6) {
      stripped = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
      if (stripped.scale() < 1) {
        stripped = stripped.setScale(1);
      }
    }
    return stripped.toPlainString();
  }
}
