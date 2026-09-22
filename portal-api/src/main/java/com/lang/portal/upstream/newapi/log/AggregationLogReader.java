package com.lang.portal.upstream.newapi.log;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.aggregation.AggregationTimeBounds;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.infrastructure.aggregation.AggregationPagedReader;
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
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "实时日志扫描范围超过当前证据允许上限，请缩小时间范围后重试");
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
              throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
            }
          }
          return new AggregationPagedReader.Page<>(fetched.total(), records);
        });
  }
}
