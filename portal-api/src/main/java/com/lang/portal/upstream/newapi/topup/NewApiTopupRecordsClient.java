package com.lang.portal.upstream.newapi.topup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.base.response.PageData;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import com.lang.portal.web.account.TopupRecord;
import com.lang.portal.web.account.TopupRecordMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiTopupRecordsClient {

  private final NewApiExchange exchange;

  public NewApiTopupRecordsClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public PageData<TopupRecord> page(NewApiSession session, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("分页参数非法");
    }
    String path = "/api/user/topup/self?page=" + page + "&page_size=" + pageSize;
    NewApiOperation op = new NewApiOperation("topup-self", HttpMethod.GET, path, true);
    NewApiRawResponse<NewApiTopupPageRaw> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiTopupPageRaw>>() {});
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    NewApiTopupPageRaw data = raw.data();
    if (data.total() < 0 || data.page() < 1 || data.pageSize() < 1) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    List<NewApiTopupEntryRaw> sorted = new ArrayList<>(data.items());
    sorted.sort((a, b) -> Long.compare(
        b.createTime() == null ? Long.MIN_VALUE : b.createTime(),
        a.createTime() == null ? Long.MIN_VALUE : a.createTime()));
    List<TopupRecord> items = new ArrayList<>();
    for (NewApiTopupEntryRaw entry : sorted) {
      if (entry == null
          || entry.tradeNo() == null
          || entry.amount() == null
          || entry.paymentMethod() == null
          || entry.status() == null
          || entry.createTime() == null
          || entry.completeTime() == null) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      items.add(
          TopupRecordMapper.from(
              entry.tradeNo(),
              entry.amount().toPlainString(),
              entry.paymentMethod(),
              entry.status(),
              entry.createTime(),
              entry.completeTime()));
    }
    return PageData.of(items, page, pageSize, data.total());
  }

  private Map<String, String> authentication(NewApiSession session) {
    if (session == null
        || session.value() == null
        || session.value().isBlank()
        || session.userId() <= 0) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    return Map.of(
        "Cookie", "session=" + session.value(), "New-Api-User", Long.toString(session.userId()));
  }
}
