package com.lang.portal.base.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuotaMoneyConverterTests {

  @Test
  void convertsQuotaToUsdWithSharedScale() {
    assertThat(QuotaMoneyConverter.toUsd(500_000L, 500_000L)).isEqualTo("1.0");
    assertThat(QuotaMoneyConverter.toUsd(0L, 500_000L)).isEqualTo("0.0");
  }

  @Test
  void rejectsNegativeQuotaAndIllegalConfig() {
    assertThatThrownBy(() -> QuotaMoneyConverter.toUsd(-1L, 500_000L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> QuotaMoneyConverter.toUsd(1L, 0L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keepsPrecisionWithoutTrailingNoise() {
    assertThat(QuotaMoneyConverter.toUsd(1L, 500_000L)).isEqualTo("0.000002");
  }
}
