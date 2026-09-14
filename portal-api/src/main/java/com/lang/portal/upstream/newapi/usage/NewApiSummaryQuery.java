package com.lang.portal.upstream.newapi.usage;

import com.lang.portal.web.usage.UsageTimeRange;

public record NewApiSummaryQuery(long startTimestamp, long endTimestamp) {

  public static NewApiSummaryQuery fromRange(UsageTimeRange range) {
    if (range == null) {
      throw new IllegalArgumentException("时间范围不能为空");
    }
    return new NewApiSummaryQuery(
        range.upstreamStartTimestamp(), range.upstreamEndTimestamp());
  }

  public String toPath() {
    return "/api/log/self/stat?start_timestamp=" + startTimestamp + "&end_timestamp=" + endTimestamp;
  }
}
