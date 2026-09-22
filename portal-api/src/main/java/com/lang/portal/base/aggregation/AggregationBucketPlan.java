package com.lang.portal.base.aggregation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AggregationBucketPlan {

  public record Bucket(Instant start, Instant end) {}

  private AggregationBucketPlan() {}

  public static List<Bucket> plan(AggregationQueryContext context) {
    List<Bucket> buckets = new ArrayList<>();
    Instant cursor = context.start();
    Instant end = context.end();
    ZoneId zone = context.zone();
    while (cursor.isBefore(end)) {
      Instant next = nextBoundary(cursor, context.granularity(), zone);
      Instant bucketEnd = next.isBefore(end) ? next : end;
      if (!bucketEnd.isAfter(cursor)) {
        break;
      }
      buckets.add(new Bucket(cursor, bucketEnd));
      cursor = bucketEnd;
    }
    return List.copyOf(buckets);
  }

  private static Instant nextBoundary(Instant cursor, AggregationGranularity granularity, ZoneId zone) {
    return switch (granularity) {
      case FIVE_MINUTES -> {
        long epochSecond = cursor.getEpochSecond();
        long next = ((epochSecond / 300) + 1) * 300;
        yield Instant.ofEpochSecond(next);
      }
      case HOUR -> {
        ZonedDateTime zoned = ZonedDateTime.ofInstant(cursor, zone);
        ZonedDateTime truncated = zoned.withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime next = truncated.plusHours(1);
        yield next.toInstant();
      }
      case DAY -> zonedStartOfNextDay(cursor, zone);
    };
  }

  private static Instant zonedStartOfNextDay(Instant cursor, ZoneId zone) {
    ZonedDateTime zoned = ZonedDateTime.ofInstant(cursor, zone);
    return zoned.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
  }
}
