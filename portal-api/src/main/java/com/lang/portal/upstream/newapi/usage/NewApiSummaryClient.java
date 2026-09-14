package com.lang.portal.upstream.newapi.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiSummaryClient {

  private final NewApiExchange exchange;

  public NewApiSummaryClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public NewApiSummary fetch(NewApiSession session, NewApiSummaryQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("查询不能为空");
    }
    NewApiOperation op = new NewApiOperation("log-self-stat", HttpMethod.GET, query.toPath(), true);
    NewApiRawResponse<NewApiSummaryRaw> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiSummaryRaw>>() {});
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    NewApiSummaryRaw data = raw.data();
    if (data.quota() == null
        || data.quota() < 0
        || data.rpm() == null
        || data.rpm() < 0
        || data.tpm() == null
        || data.tpm() < 0) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return new NewApiSummary(data.quota(), data.rpm(), data.tpm());
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
