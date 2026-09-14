package com.lang.portal.upstream.newapi.usage;

import com.lang.portal.web.usage.UsageTimeRange;

public record NewApiHourlyQuery(long startTimestamp, long endTimestamp) {

  public static NewApiHourlyQuery fromRange(UsageTimeRange range) {
    if (range == null) {
      throw new IllegalArgumentException("时间范围不能为空");
    }
    return new NewApiHourlyQuery(
        range.upstreamStartTimestamp(), range.upstreamEndTimestamp());
  }

  public String toPath() {
    return "/api/data/self?start_timestamp=" + startTimestamp + "&end_timestamp=" + endTimestamp;
  }
}
