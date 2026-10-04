package com.lang.portal.upstream.newapi.log;

import com.lang.portal.base.aggregation.AggregationBaselinePolicy;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;

public final class AggregationLogConverter {

  private AggregationLogConverter() {}

  public static AggregationLogRecord fromUpstream(NewApiLogRecord upstream, String baselineVersion) {
    if (upstream == null) {
      throw new IllegalArgumentException("上游日志记录不能为空");
    }
    AggregationBaselinePolicy.requireSupported(baselineVersion);
    AggregationLogResult result =
        switch (upstream.result()) {
          case SUCCESS -> AggregationLogResult.SUCCESS;
          case ERROR -> AggregationLogResult.ERROR;
        };
    return new AggregationLogRecord(
        upstream.occurredAt(),
        result,
        upstream.tokenId(),
        blankToNull(upstream.model()),
        blankToNull(upstream.requestId()),
        blankToNull(upstream.keyName()),
        upstream.inputTokens(),
        upstream.outputTokens(),
        upstream.durationMs(),
        upstream.stream(),
        upstream.quota());
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
