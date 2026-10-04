package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AggregationBaselinePolicyTests {

  @Test
  void supportsFrozenBaselineVersion() {
    assertThat(AggregationBaselinePolicy.isSupported("p2-2026-09-22-a")).isTrue();
  }

  @Test
  void unknownBaselineVersionIsUnsupported() {
    assertThat(AggregationBaselinePolicy.isSupported("unknown-version")).isFalse();
    assertThatThrownBy(() -> AggregationBaselinePolicy.requireSupported("unknown-version"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-version");
  }

  @Test
  void verifiedFieldsAreAvailableForFormalComputation() {
    assertThat(AggregationBaselinePolicy.fieldStatus("p2-2026-09-22-a", AggregationField.TOKEN_ID))
        .isEqualTo(FieldSupport.VERIFIED);
    assertThat(AggregationBaselinePolicy.fieldStatus("p2-2026-09-22-a", AggregationField.RAW_QUOTA))
        .isEqualTo(FieldSupport.VERIFIED);
  }

  @Test
  void requestTotalSuccessRateAndUsdAmountAreNotVerified() {
    assertThat(AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.REQUEST_TOTAL))
        .isNotEqualTo(FieldSupport.VERIFIED);
    assertThat(AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.SUCCESS_RATE))
        .isNotEqualTo(FieldSupport.VERIFIED);
    assertThat(AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.USD_AMOUNT))
        .isNotEqualTo(FieldSupport.VERIFIED);
  }
}
