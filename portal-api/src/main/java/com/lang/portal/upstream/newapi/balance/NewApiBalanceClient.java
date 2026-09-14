package com.lang.portal.upstream.newapi.balance;

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
public class NewApiBalanceClient {

  private final NewApiExchange exchange;

  public NewApiBalanceClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public long currentQuota(NewApiSession session) {
    NewApiOperation op = new NewApiOperation("balance-self", HttpMethod.GET, "/api/user/self", true);
    NewApiRawResponse<NewApiBalanceRaw> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiBalanceRaw>>() {});
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    Long quota = raw.data().quota();
    if (quota == null || quota < 0) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return quota;
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
