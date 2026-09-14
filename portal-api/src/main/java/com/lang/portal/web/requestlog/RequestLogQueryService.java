package com.lang.portal.web.requestlog;

import com.lang.portal.base.money.QuotaMoneyConverter;
import com.lang.portal.base.response.PageData;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.web.usage.UsageTimeRange;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RequestLogQueryService {

  private final NewApiLogClient logClient;
  private final PortalCommonProperties properties;

  public RequestLogQueryService(NewApiLogClient logClient, PortalCommonProperties properties) {
    this.logClient = logClient;
    this.properties = properties;
  }

  public PageData<RequestLogDto> list(
      NewApiSession session,
      int page,
      int pageSize,
      String result,
      String keyName,
      String model,
      String startTime,
      String endTime,
      Clock clock) {
    if (!"SUCCESS".equals(result) && !"ERROR".equals(result)) {
      throw new IllegalArgumentException("结果类型只允许 SUCCESS 或 ERROR");
    }
    UsageTimeRange range = UsageTimeRange.resolve(startTime, endTime, clock);
    NewApiLogQuery query = NewApiLogQuery.fromRange(page, pageSize, keyName, model, range);
    NewApiLogPage logPage =
        "ERROR".equals(result)
            ? logClient.listError(session, query)
            : logClient.listSuccess(session, query);
    long quotaPerUsd = properties.catalog().quotaPerUsd();
    List<RequestLogDto> items = new ArrayList<>();
    for (NewApiLogRecord record : logPage.items()) {
      items.add(toDto(record, quotaPerUsd));
    }
    return PageData.of(items, page, pageSize, logPage.total());
  }

  private RequestLogDto toDto(NewApiLogRecord record, long quotaPerUsd) {
    return new RequestLogDto(
        record.occurredAt().toString(),
        record.requestId(),
        record.keyName(),
        record.model(),
        record.result().name(),
        record.inputTokens(),
        record.outputTokens(),
        record.durationMs(),
        record.stream(),
        Long.toString(record.quota()),
        QuotaMoneyConverter.toUsd(record.quota(), quotaPerUsd),
        "USD",
        null,
        null);
  }
}
