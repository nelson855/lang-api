package com.lang.portal.upstream.newapi.log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiLogClient {

  private final NewApiExchange exchange;

  public NewApiLogClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public NewApiLogPage listSuccess(NewApiSession session, NewApiLogQuery query) {
    return list(session, NewApiLogResult.SUCCESS, query);
  }

  public NewApiLogPage listError(NewApiSession session, NewApiLogQuery query) {
    return list(session, NewApiLogResult.ERROR, query);
  }

  NewApiLogPage list(NewApiSession session, NewApiLogResult result, NewApiLogQuery query) {
    if (query == null || result == null) {
      throw new IllegalArgumentException("查询与结果类型不能为空");
    }
    NewApiOperation op =
        new NewApiOperation("log-self", HttpMethod.GET, query.toPath(result), true);
    NewApiRawResponse<NewApiLogPageRaw> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiLogPageRaw>>() {});
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    return NewApiLogMapper.mapPage(raw.data(), result);
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
