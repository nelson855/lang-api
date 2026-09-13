package com.lang.portal.web.apikey;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.token.NewApiCreateTokenCommand;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.NewApiUpdateTokenCommand;
import com.lang.portal.upstream.newapi.token.SensitiveSecret;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyApplicationService {

  private final NewApiTokenClient client;
  private final ApiKeySecurityEvents events;

  public ApiKeyApplicationService(NewApiTokenClient client, ApiKeySecurityEvents events) {
    this.client = client;
    this.events = events;
  }

  public ApiKeyCreateResponse create(NewApiSession session, CreateApiKeyRequest request) {
    return audited("create", session, "", () -> doCreate(session, request));
  }

  private ApiKeyCreateResponse doCreate(NewApiSession session, CreateApiKeyRequest request) {
    requireSession(session);
    if (request == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 body 不合法");
    }
    boolean unlimited = Boolean.TRUE.equals(request.unlimited());
    String name = ApiKeyConstraints.name(request.name());
    long remaining = ApiKeyConstraints.remaining(unlimited, request.remaining());
    Long expired = toEpochSecond(ApiKeyConstraints.expiresAt(parseExpires(request.expiresAt()), Instant.now()));
    List<String> models = ApiKeyConstraints.models(request.models());
    List<String> ips = ApiKeyConstraints.ips(request.ips());
    client.createToken(session, new NewApiCreateTokenCommand(name, unlimited, remaining, expired, models, ips));
    return new ApiKeyCreateResponse(true);
  }

  public ApiKeyUpdateResponse update(NewApiSession session, long id, UpdateApiKeyRequest request) {
    return audited("update", session, Long.toString(id), () -> doUpdate(session, id, request));
  }

  private ApiKeyUpdateResponse doUpdate(NewApiSession session, long id, UpdateApiKeyRequest request) {
    requireId(id);
    requireSession(session);
    if (request == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 body 不合法");
    }
    String name = request.name() == null ? null : ApiKeyConstraints.name(request.name());
    Boolean unlimited = request.unlimited();
    Long remaining = request.remaining();
    if (Boolean.TRUE.equals(unlimited) && remaining != null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 quota 不合法");
    }
    if (Boolean.FALSE.equals(unlimited) && remaining == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 quota 不合法");
    }
    Long validatedRemaining = null;
    if (remaining != null) {
      validatedRemaining = ApiKeyConstraints.remaining(Boolean.TRUE.equals(unlimited), remaining);
    }
    Long expired = null;
    if (request.expiresAt() != null) {
      expired = toEpochSecond(ApiKeyConstraints.expiresAt(parseExpires(request.expiresAt()), Instant.now()));
    }
    List<String> models = request.models() == null ? null : ApiKeyConstraints.models(request.models());
    List<String> ips = request.ips() == null ? null : ApiKeyConstraints.ips(request.ips());
    client.updateToken(
        session, id, new NewApiUpdateTokenCommand(name, unlimited, validatedRemaining, expired, models, ips));
    return new ApiKeyUpdateResponse(true);
  }

  public ApiKeyStatusResponse updateStatus(NewApiSession session, long id, Boolean enabled) {
    return audited("status", session, Long.toString(id), () -> doUpdateStatus(session, id, enabled));
  }

  private ApiKeyStatusResponse doUpdateStatus(NewApiSession session, long id, Boolean enabled) {
    requireId(id);
    requireSession(session);
    if (enabled == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 enabled 不合法");
    }
    client.updateStatus(session, id, enabled);
    return new ApiKeyStatusResponse(enabled);
  }

  public ApiKeyDeleteResponse delete(NewApiSession session, long id) {
    return audited("delete", session, Long.toString(id), () -> doDelete(session, id));
  }

  private ApiKeyDeleteResponse doDelete(NewApiSession session, long id) {
    requireId(id);
    requireSession(session);
    client.deleteToken(session, id);
    return new ApiKeyDeleteResponse(true);
  }

  public ApiKeyRevealResponse reveal(NewApiSession session, long id) {
    return audited("reveal", session, Long.toString(id), () -> doReveal(session, id));
  }

  private ApiKeyRevealResponse doReveal(NewApiSession session, long id) {
    requireId(id);
    requireSession(session);
    SensitiveSecret secret = client.revealToken(session, id);
    try {
      return new ApiKeyRevealResponse(secret.asString());
    } finally {
      secret.clear();
    }
  }

  private <T> T audited(String op, NewApiSession session, String resourceId, Supplier<T> action) {
    try {
      T result = action.get();
      events.completed(op, session, resourceId, "success", "OK");
      return result;
    } catch (PortalException e) {
      events.completed(op, session, resourceId, "failure", e.errorCode().name());
      throw e;
    }
  }

  private Instant parseExpires(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 expiresAt 不合法");
    }
  }

  private Long toEpochSecond(Instant value) {
    return value == null ? null : value.getEpochSecond();
  }

  private void requireId(long id) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
  }

  private void requireSession(NewApiSession session) {
    if (session == null) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
  }
}
