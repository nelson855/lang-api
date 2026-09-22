package com.lang.portal.infrastructure.aggregation;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record AggregationCacheKey(
    String operation,
    String userId,
    Instant start,
    Instant end,
    AggregationGranularity granularity,
    String zoneId,
    String baselineVersion,
    Map<String, String> filters) {

  public AggregationCacheKey {
    if (operation == null || operation.isBlank()) {
      throw new IllegalArgumentException("缓存键操作命名空间不能为空");
    }
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("缓存键用户不能为空");
    }
    Objects.requireNonNull(start, "缓存键开始时间不能为空");
    Objects.requireNonNull(end, "缓存键结束时间不能为空");
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("缓存键开始时间必须早于结束时间");
    }
    Objects.requireNonNull(granularity, "缓存键粒度不能为空");
    if (zoneId == null || zoneId.isBlank()) {
      throw new IllegalArgumentException("缓存键时区不能为空");
    }
    if (baselineVersion == null || baselineVersion.isBlank()) {
      throw new IllegalArgumentException("缓存键口径版本不能为空");
    }
    filters = filters == null || filters.isEmpty() ? Map.of() : Map.copyOf(new TreeMap<>(filters));
  }

  public static AggregationCacheKey of(
      String operation, String userId, AggregationQueryContext context, Map<String, String> filters) {
    Objects.requireNonNull(context, "查询上下文不能为空");
    return new AggregationCacheKey(
        operation,
        userId,
        context.start(),
        context.end(),
        context.granularity(),
        context.zone().getId(),
        context.baselineVersion(),
        filters);
  }
}
