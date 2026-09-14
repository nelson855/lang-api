package com.lang.portal.web.usage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record UsageTimeRange(Instant start, Instant end) {

  private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
  private static final Duration MAX_RANGE = Duration.ofDays(30);

  public static UsageTimeRange resolve(String startText, String endText, Clock clock) {
    boolean hasStart = startText != null && !startText.isBlank();
    boolean hasEnd = endText != null && !endText.isBlank();
    if (!hasStart && !hasEnd) {
      Instant end = clock.instant();
      return new UsageTimeRange(end.minus(DEFAULT_RANGE), end);
    }
    if (hasStart != hasEnd) {
      throw new IllegalArgumentException("开始与结束时间必须同时提供或同时省略");
    }
    Instant start = parseSecondPrecision(startText);
    Instant end = parseSecondPrecision(endText);
    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("开始时间必须早于结束时间");
    }
    if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
      throw new IllegalArgumentException("时间跨度不得超过 30 天");
    }
    return new UsageTimeRange(start, end);
  }

  public long upstreamStartTimestamp() {
    return start.getEpochSecond();
  }

  public long upstreamEndTimestamp() {
    return end.getEpochSecond() - 1;
  }

  private static Instant parseSecondPrecision(String text) {
    OffsetDateTime parsed;
    try {
      parsed = OffsetDateTime.parse(text.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("时间必须为带时区 ISO 8601", e);
    }
    if (parsed.getNano() != 0) {
      throw new IllegalArgumentException("时间只允许秒级精度");
    }
    return parsed.toInstant();
  }
}
