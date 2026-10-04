package com.lang.portal.upstream.newapi.log;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.aggregation.AggregationTimeBounds;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.infrastructure.aggregation.AggregationPagedReader;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.infrastructure.aggregation.AggregationOutcome;
import com.lang.portal.infrastructure.aggregation.AggregationSource;
import com.lang.portal.base.aggregation.ProtectReason;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import java.time.Duration;
import java.util.List;

public final class AggregationLogReader {

  private AggregationLogReader() {}

  public static List<AggregationLogRecord> readSuccessLogs(
      NewApiSession session,
      AggregationQueryContext context,
      String keyName,
      String model,
      AggregationReadBudget budget,
      int pageSize,
      Duration maxLiveLogRange,
      NewApiLogClient logClient) {
    return read(session, context, NewApiLogResult.SUCCESS, keyName, model, budget, pageSize, maxLiveLogRange, logClient);
  }

  /** 一次缓存加载只收口一次；失败时仍保留已接纳页与记录。 */
  public static List<AggregationLogRecord> readSuccessLogs(
      NewApiSession session, AggregationQueryContext context, String keyName, String model,
      AggregationReadBudget budget, int pageSize, Duration maxLiveLogRange,
      NewApiLogClient logClient, AggregationMetrics metrics, String operation) {
    AggregationOutcome outcome = AggregationOutcome.FAILURE;
    int initialPages = budget.usedPages();
    int initialRecords = budget.usedRecords();
    try {
      List<AggregationLogRecord> records = readSuccessLogs(
          session, context, keyName, model, budget, pageSize, maxLiveLogRange, logClient);
      outcome = AggregationOutcome.SUCCESS;
      return records;
    } catch (RuntimeException e) {
      ProtectReason reason = budget.protectionReason();
      if (reason == null && e instanceof PortalException failure
          && failure.errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT) {
        reason = ProtectReason.SINGLE_TIMEOUT;
      }
      if (reason != null) {
        metrics.recordProtection(operation, reason);
      }
      throw e;
    } finally {
      metrics.recordUpstream(AggregationSource.SUCCESS_LOG, outcome,
          budget.usedPages() - initialPages, budget.usedRecords() - initialRecords);
    }
  }

  public static List<AggregationLogRecord> readErrorLogs(
      NewApiSession session,
      AggregationQueryContext context,
      String keyName,
      String model,
      AggregationReadBudget budget,
      int pageSize,
      Duration maxLiveLogRange,
      NewApiLogClient logClient) {
    return read(session, context, NewApiLogResult.ERROR, keyName, model, budget, pageSize, maxLiveLogRange, logClient);
  }

  private static List<AggregationLogRecord> read(
      NewApiSession session,
      AggregationQueryContext context,
      NewApiLogResult result,
      String keyName,
      String model,
      AggregationReadBudget budget,
      int pageSize,
      Duration maxLiveLogRange,
      NewApiLogClient logClient) {
    if (session == null || context == null || budget == null || logClient == null) {
      throw new IllegalArgumentException("会话、查询上下文、预算与日志客户端均为必填");
    }
    if (maxLiveLogRange == null
        || Duration.between(context.start(), context.end()).compareTo(maxLiveLogRange) > 0) {
      throw budget.reject(ProtectReason.RANGE);
    }
    long startTs = context.start().getEpochSecond();
    long endTs = AggregationTimeBounds.upstreamInclusiveEnd(context).getEpochSecond();
    return AggregationPagedReader.readAll(
        budget,
        pageSize,
        page -> {
          NewApiLogQuery query = NewApiLogQuery.of(page, pageSize, keyName, model, startTs, endTs);
          NewApiLogPage fetched =
              result == NewApiLogResult.SUCCESS
                  ? logClient.listSuccess(session, query)
                  : logClient.listError(session, query);
          List<AggregationLogRecord> records =
              fetched.items().stream()
                  .map(entry -> AggregationLogConverter.fromUpstream(entry, context.baselineVersion()))
                  .toList();
          for (AggregationLogRecord record : records) {
            if (!AggregationTimeBounds.contains(context, record.occurredAt())) {
              throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
            }
          }
          return new AggregationPagedReader.Page<>(fetched.total(), records);
        });
  }
}
