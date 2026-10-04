package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AggregationFormalValuesTests {

  @Test
  void usdAmountStaysUnavailableWithoutFrozenQuotaPerUsd() {
    assertThat(AggregationFormalValues.formalUsdAmount(new BigDecimal("500"), "p2-2026-09-22-a", 500000L))
        .isEmpty();
  }

  @Test
  void successRateStaysUnavailableWhileType5Unfrozen() {
    assertThat(AggregationFormalValues.formalSuccessRate(8L, 10L, "p2-2026-09-22-a")).isEmpty();
  }
}
