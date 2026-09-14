package com.lang.portal.web.usage;

import com.lang.portal.base.money.QuotaMoneyConverter;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.usage.HourlyAggregator;
import com.lang.portal.upstream.newapi.usage.HourlyBucket;
import com.lang.portal.upstream.newapi.usage.NewApiHourlyClient;
import com.lang.portal.upstream.newapi.usage.NewApiHourlyQuery;
import com.lang.portal.upstream.newapi.usage.NewApiHourlyRow;
import com.lang.portal.upstream.newapi.usage.NewApiSummary;
import com.lang.portal.upstream.newapi.usage.NewApiSummaryClient;
import com.lang.portal.upstream.newapi.usage.NewApiSummaryQuery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UsageQueryService {

  private final NewApiSummaryClient summaryClient;
  private final NewApiHourlyClient hourlyClient;
  private final PortalCommonProperties properties;

  public UsageQueryService(
      NewApiSummaryClient summaryClient,
      NewApiHourlyClient hourlyClient,
      PortalCommonProperties properties) {
    this.summaryClient = summaryClient;
    this.hourlyClient = hourlyClient;
    this.properties = properties;
  }

  public UsageSummaryDto summary(
      NewApiSession session, String startTime, String endTime, Clock clock) {
    UsageTimeRange range = UsageTimeRange.resolve(startTime, endTime, clock);
    NewApiSummary summary =
        summaryClient.fetch(session, NewApiSummaryQuery.fromRange(range));
    long quotaPerUsd = properties.catalog().quotaPerUsd();
    return new UsageSummaryDto(
        Long.toString(summary.quota()),
        QuotaMoneyConverter.toUsd(summary.quota(), quotaPerUsd),
        "USD",
        summary.rpm(),
        summary.tpm(),
        60);
  }

  public UsageTimeseriesDto timeseries(
      NewApiSession session, String startTime, String endTime, Clock clock) {
    UsageTimeRange range = UsageTimeRange.resolve(startTime, endTime, clock);
    List<NewApiHourlyRow> rows =
        hourlyClient.fetch(session, NewApiHourlyQuery.fromRange(range));
    List<HourlyBucket> buckets =
        HourlyAggregator.aggregate(rows, range.start(), range.end());
    long quotaPerUsd = properties.catalog().quotaPerUsd();
    List<UsageTimeseriesPoint> points = new ArrayList<>();
    for (HourlyBucket bucket : buckets) {
      points.add(
          new UsageTimeseriesPoint(
              bucket.bucketStart().toString(),
              bucket.requestCount(),
              bucket.tokenCount(),
              Long.toString(bucket.quota()),
              QuotaMoneyConverter.toUsd(bucket.quota(), quotaPerUsd)));
    }
    return new UsageTimeseriesDto("HOUR", points);
  }
}
