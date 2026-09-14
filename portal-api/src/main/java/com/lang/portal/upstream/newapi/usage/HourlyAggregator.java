package com.lang.portal.upstream.newapi.usage;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HourlyAggregator {
  private HourlyAggregator() {}

  public static List<HourlyBucket> aggregate(
      List<NewApiHourlyRow> rows, Instant rangeStart, Instant rangeEnd) {
    if (rangeStart == null || rangeEnd == null || !rangeStart.isBefore(rangeEnd)) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    Map<Long, long[]> merged = new LinkedHashMap<>();
    if (rows != null) {
      for (NewApiHourlyRow row : rows) {
        if (row == null
            || row.hourEpochSecond() < 0
            || row.hourEpochSecond() % 3600 != 0
            || row.requestCount() < 0
            || row.tokenCount() < 0
            || row.quota() < 0) {
          throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
        }
        Instant bucket = Instant.ofEpochSecond(row.hourEpochSecond());
        if (bucket.isBefore(rangeStart) || !bucket.isBefore(rangeEnd)) {
          throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
        }
        long[] acc = merged.computeIfAbsent(row.hourEpochSecond(), k -> new long[3]);
        acc[0] += row.requestCount();
        acc[1] += row.tokenCount();
        acc[2] += row.quota();
      }
    }
    List<HourlyBucket> buckets = new ArrayList<>();
    merged.forEach(
        (hour, acc) ->
            buckets.add(
                new HourlyBucket(Instant.ofEpochSecond(hour), acc[0], acc[1], acc[2])));
    buckets.sort(Comparator.comparing(HourlyBucket::bucketStart));
    return List.copyOf(buckets);
  }
}
