package com.lang.portal.web.usage;

import java.util.List;

public record UsageTimeseriesDto(String granularity, List<UsageTimeseriesPoint> points) {
  public UsageTimeseriesDto {
    points = points == null ? List.of() : List.copyOf(points);
  }
}
