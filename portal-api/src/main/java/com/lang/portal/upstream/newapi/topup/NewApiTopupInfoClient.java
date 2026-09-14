package com.lang.portal.upstream.newapi.topup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import com.lang.portal.web.account.TopupCapability;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiTopupInfoClient {

  private final NewApiExchange exchange;

  public NewApiTopupInfoClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public TopupCapability capability(NewApiSession session) {
    NewApiOperation op = new NewApiOperation("topup-info", HttpMethod.GET, "/api/user/topup/info", true);
    NewApiRawResponse<NewApiTopupInfoRaw> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiTopupInfoRaw>>() {});
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    NewApiTopupInfoRaw data = raw.data();
    if (data.online() == null
        || data.stripe() == null
        || data.creem() == null
        || data.waffo() == null
        || data.waffoPancake() == null) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return TopupCapability.fromUpstreamFlags(
        data.online(), data.stripe(), data.creem(), data.waffo(), data.waffoPancake());
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
