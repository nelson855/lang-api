package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.aggregation.AggregationCache;
import com.lang.portal.infrastructure.aggregation.AggregationCacheKey;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.infrastructure.aggregation.AggregationOutcome;
import com.lang.portal.infrastructure.aggregation.AggregationSource;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.AggregationLogReader;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccountConsumptionSnapshotService {

  private static final String SNAPSHOT_OPERATION = AccountAggregationOperations.SNAPSHOT;

  private final NewApiLogClient logClient;
  private final PortalCommonProperties properties;
  private final AggregationMetrics metrics;
  private final AggregationCache<ConsumptionSnapshot> cache;

  @Autowired
  public AccountConsumptionSnapshotService(
      NewApiLogClient logClient, PortalCommonProperties properties, AggregationMetrics metrics) {
    this(
        logClient,
        properties,
        metrics,
        AggregationCache.fromAggregationConfig(properties.aggregation()));
  }

  AccountConsumptionSnapshotService(
      NewApiLogClient logClient,
      PortalCommonProperties properties,
      AggregationMetrics metrics,
      AggregationCache<ConsumptionSnapshot> cache) {
    this.logClient = logClient;
    this.properties = properties;
    this.metrics = metrics;
    this.cache = cache;
  }

  public ConsumptionSnapshot loadSnapshot(
      NewApiSession session, AggregationQueryContext context, Clock clock) {
    return loadSnapshotInternal(session, context, Map.of(), clock);
  }

  public ConsumptionSnapshot loadSnapshotForTransactions(
      NewApiSession session,
      AggregationQueryContext context,
      AccountTransactionType requestedType,
      int page,
      int pageSize,
      Clock clock) {
    Map<String, String> filters = new HashMap<>();
    if (requestedType != null) {
      filters.put("type", requestedType.name());
    }
    filters.put("page", Integer.toString(page));
    filters.put("pageSize", Integer.toString(pageSize));
    return loadSnapshotInternal(session, context, filters, clock);
  }

  private ConsumptionSnapshot loadSnapshotInternal(
      NewApiSession session,
      AggregationQueryContext context,
      Map<String, String> filters,
      Clock clock) {
    AggregationCacheKey key =
        AggregationCacheKey.of(
            SNAPSHOT_OPERATION, Long.toString(session.userId()), context, filters);
    Instant start = clock.instant();
    try {
      AggregationCache.LoadResult<ConsumptionSnapshot> loaded =
          cache.getWithOutcome(key, k -> fetch(session, context, clock));
      metrics.recordCache(SNAPSHOT_OPERATION, loaded.outcome());
      metrics.recordAggregation(
          SNAPSHOT_OPERATION,
          AggregationOutcome.SUCCESS,
          Duration.between(start, clock.instant()));
      return loaded.value();
    } catch (RuntimeException e) {
      metrics.recordAggregation(
          SNAPSHOT_OPERATION, AggregationOutcome.FAILURE, Duration.between(start, clock.instant()));
      throw e;
    }
  }

  private ConsumptionSnapshot fetch(
      NewApiSession session, AggregationQueryContext context, Clock clock) {
    PortalCommonProperties.Aggregation agg = properties.aggregation();
    AggregationReadBudget budget =
        new AggregationReadBudget(
            agg.maxPages(), agg.maxRecords(), clock.instant().plus(agg.totalTimeout()), clock);
    List<AggregationLogRecord> records =
        AggregationLogReader.readSuccessLogs(
            session, context, null, null, budget, agg.pageSize(), agg.maxLiveLogRange(), logClient);
    metrics.recordUpstream(
        AggregationSource.SUCCESS_LOG, AggregationOutcome.SUCCESS, budget.usedPages(), records.size());
    return ConsumptionSnapshot.from(records);
  }
}
