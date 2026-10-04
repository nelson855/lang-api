package com.lang.portal.base.aggregation;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public record AggregationQueryContext(
    Instant start, Instant end, ZoneId zone, AggregationGranularity granularity, String baselineVersion) {

  public static AggregationQueryContext of(
      String startIso,
      String endIso,
      String timezoneId,
      AggregationGranularity granularity,
      String baselineVersion,
      PortalCommonProperties.Aggregation limits) {
    if (startIso == null || startIso.isBlank() || endIso == null || endIso.isBlank()) {
      throw new IllegalArgumentException("聚合查询起止时间均为必填");
    }
    if (timezoneId == null || timezoneId.isBlank()) {
      throw new IllegalArgumentException("聚合查询时区为必填");
    }
    if (granularity == null) {
      throw new IllegalArgumentException("聚合查询粒度为必填");
    }
    if (baselineVersion == null || baselineVersion.isBlank()) {
      throw new IllegalArgumentException("聚合查询口径版本为必填");
    }
    AggregationBaselinePolicy.requireSupported(baselineVersion);
    ZoneId zone;
    try {
      zone = ZoneId.of(timezoneId);
    } catch (Exception e) {
      throw new IllegalArgumentException("未知时区 " + timezoneId, e);
    }
    if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
      throw new IllegalArgumentException("非 IANA 时区 " + timezoneId);
    }
    OffsetDateTime startParsed = parseSecondPrecision(startIso);
    OffsetDateTime endParsed = parseSecondPrecision(endIso);
    Instant start = startParsed.toInstant();
    Instant end = endParsed.toInstant();
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("聚合查询开始时间必须早于结束时间");
    }
    Duration span = Duration.between(start, end);
    Duration allowed = allowedSpan(granularity, limits);
    if (span.compareTo(allowed) > 0) {
      throw new IllegalArgumentException("聚合查询跨度超出粒度允许范围");
    }
    return new AggregationQueryContext(start, end, zone, granularity, baselineVersion);
  }

  private static OffsetDateTime parseSecondPrecision(String value) {
    OffsetDateTime parsed;
    try {
      parsed = OffsetDateTime.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("时间必须为带偏移量的 ISO 8601 " + value, e);
    }
    if (parsed.getNano() != 0) {
      throw new IllegalArgumentException("时间仅支持秒级精度 " + value);
    }
    if (parsed.getOffset() == null) {
      throw new IllegalArgumentException("时间必须携带偏移量 " + value);
    }
    return parsed;
  }

  private static Duration allowedSpan(
      AggregationGranularity granularity, PortalCommonProperties.Aggregation limits) {
    if (limits == null) {
      throw new IllegalArgumentException("聚合配置缺失");
    }
    return switch (granularity) {
      case FIVE_MINUTES -> limits.fiveMinutesMaxSpan();
      case HOUR -> limits.hourMaxSpan();
      case DAY -> limits.dayMaxSpan();
    };
  }
}
