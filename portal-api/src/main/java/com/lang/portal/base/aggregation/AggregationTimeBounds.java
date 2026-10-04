package com.lang.portal.base.aggregation;

import java.time.Instant;

public final class AggregationTimeBounds {

  private AggregationTimeBounds() {}

  public static boolean contains(AggregationQueryContext context, Instant timestamp) {
    return !timestamp.isBefore(context.start()) && timestamp.isBefore(context.end());
  }

  public static Instant upstreamInclusiveEnd(AggregationQueryContext context) {
    return context.end().minusSeconds(1);
  }
}
