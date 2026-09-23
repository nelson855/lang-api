package com.lang.portal.web.dashboard;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.aggregation.AggregationCache;
import com.lang.portal.infrastructure.aggregation.AggregationCacheKey;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.infrastructure.aggregation.AggregationOutcome;
import com.lang.portal.infrastructure.aggregation.AggregationSource;
import com.lang.portal.infrastructure.aggregation.CacheOutcome;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.AggregationLogReader;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DashboardStatsQueryService {

  private static final String OPERATION = "dashboard-stats";

  private final NewApiLogClient logClient;
  private final PortalCommonProperties properties;
  private final AggregationMetrics metrics;
  private final AggregationCache<DashboardStatsData> cache;

  @Autowired
  public DashboardStatsQueryService(
      NewApiLogClient logClient,
      PortalCommonProperties properties,
      AggregationMetrics metrics) {
    this(
        logClient,
        properties,
        metrics,
        AggregationCache.fromAggregationConfig(properties.aggregation()));
  }

  DashboardStatsQueryService(
      NewApiLogClient logClient,
      PortalCommonProperties properties,
      AggregationMetrics metrics,
      AggregationCache<DashboardStatsData> cache) {
    this.logClient = logClient;
    this.properties = properties;
    this.metrics = metrics;
    this.cache = cache;
  }

  public DashboardStatsData query(
      NewApiSession session,
      String userId,
      String startTime,
      String endTime,
      String timezone,
      String granularity,
      Clock clock) {
    PortalCommonProperties.Aggregation agg = properties.aggregation();
    AggregationQueryContext context =
        AggregationQueryContext.of(
            startTime,
            endTime,
            timezone,
            parseGranularity(granularity),
            agg.baselineVersion(),
            agg);
    AggregationCacheKey key = AggregationCacheKey.of(OPERATION, userId, context, Map.of());
    Instant start = clock.instant();
    try {
      AggregationCache.LoadResult<DashboardStatsData> loaded =
          cache.getWithOutcome(key, k -> load(session, context, clock));
      metrics.recordCache(OPERATION, loaded.outcome());
      if (loaded.outcome() != CacheOutcome.HIT) {
        metrics.recordAggregation(
            OPERATION, AggregationOutcome.SUCCESS, Duration.between(start, clock.instant()));
      }
      return loaded.value();
    } catch (RuntimeException e) {
      metrics.recordAggregation(
          OPERATION, AggregationOutcome.FAILURE, Duration.between(start, clock.instant()));
      throw e;
    }
  }

  private DashboardStatsData load(
      NewApiSession session, AggregationQueryContext context, Clock clock) {
    PortalCommonProperties.Aggregation agg = properties.aggregation();
    AggregationReadBudget budget =
        new AggregationReadBudget(
            agg.maxPages(), agg.maxRecords(), clock.instant().plus(agg.totalTimeout()), clock);
    List<AggregationLogRecord> records =
        AggregationLogReader.readSuccessLogs(
            session, context, null, null, budget, agg.pageSize(), agg.maxLiveLogRange(), logClient);
    metrics.recordUpstream(
        AggregationSource.SUCCESS_LOG,
        AggregationOutcome.SUCCESS,
        budget.usedPages(),
        records.size());
    DashboardStatsData computed = DashboardStatsCalculator.calculate(records, context.baselineVersion());
    DashboardRangeDto range =
        new DashboardRangeDto(
            context.start().toString(),
            context.end().toString(),
            context.zone().getId(),
            context.granularity().name());
    return new DashboardStatsData(
        computed.baselineVersion(),
        range,
        computed.metrics(),
        computed.requestTrend(),
        computed.spendTrend(),
        computed.recentRequests());
  }

  private static AggregationGranularity parseGranularity(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("聚合查询粒度为必填");
    }
    return AggregationGranularity.valueOf(raw.trim());
  }
}
