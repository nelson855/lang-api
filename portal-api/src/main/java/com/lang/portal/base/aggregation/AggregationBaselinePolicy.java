package com.lang.portal.base.aggregation;

public final class AggregationBaselinePolicy {

  public static final String FROZEN_BASELINE = "p2-2026-09-22-a";

  private AggregationBaselinePolicy() {}

  public static boolean isSupported(String baselineVersion) {
    return FROZEN_BASELINE.equals(baselineVersion);
  }

  public static void requireSupported(String baselineVersion) {
    if (!isSupported(baselineVersion)) {
      throw new IllegalArgumentException("不受支持的聚合口径版本 " + baselineVersion);
    }
  }

  public static FieldSupport fieldStatus(String baselineVersion, AggregationField field) {
    requireSupported(baselineVersion);
    return switch (field) {
      case OCCURRED_TIME,
          INPUT_TOKENS,
          OUTPUT_TOKENS,
          ELAPSED_MS,
          TOKEN_ID,
          MODEL,
          REQUEST_ID,
          RAW_QUOTA -> FieldSupport.VERIFIED;
      case LOG_TYPE, LOG_STATUS -> FieldSupport.CONDITIONAL;
    };
  }

  public static FieldSupport metricStatus(String baselineVersion, AggregationMetric metric) {
    requireSupported(baselineVersion);
    return switch (metric) {
      case TOKEN_USAGE, ACTIVE_KEYS, AVG_LATENCY -> FieldSupport.VERIFIED;
      case REQUEST_TOTAL, SUCCESS_RATE, USD_AMOUNT -> FieldSupport.CONDITIONAL;
    };
  }
}
