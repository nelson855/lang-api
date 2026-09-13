package com.lang.portal.upstream.newapi.token;

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
public class NewApiTokenClient {

  private final NewApiExchange exchange;

  public NewApiTokenClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public NewApiTokenPage listTokens(NewApiSession session, int page, int pageSize) {
    checkPage(page, pageSize);
    String path = "/api/token/?page=" + page + "&page_size=" + pageSize;
    NewApiOperation op = new NewApiOperation("token-list", HttpMethod.GET, path, true);
    NewApiRawResponse<NewApiTokenPage> raw =
        exchange.executeRaw(op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiTokenPage>>() {});
    throwIfStatus(raw);
    if (raw.data() == null) {
      throw exchange.failure(raw);
    }
    return raw.data();
  }

  public NewApiTokenPage searchTokens(NewApiSession session, String keyword, int page, int pageSize) {
    checkPage(page, pageSize);
    String path = NewApiTokenSearch.searchPath(keyword, page, pageSize);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    NewApiRawResponse<NewApiTokenPage> raw =
        exchange.executeRaw(op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiTokenPage>>() {});
    throwIfStatus(raw);
    if (raw.data() == null) {
      throw exchange.failure(raw);
    }
    return raw.data();
  }

  public NewApiToken getToken(NewApiSession session, long id) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    String path = "/api/token/" + id;
    NewApiOperation op = new NewApiOperation("token-get", HttpMethod.GET, path, true);
    NewApiRawResponse<NewApiToken> raw =
        exchange.executeRaw(op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiToken>>() {});
    throwIfStatus(raw);
    if (raw.data() == null || raw.data().id() == null) {
      throw exchange.failure(raw);
    }
    return raw.data();
  }

  public void createToken(NewApiSession session, NewApiCreateTokenCommand command) {
    if (command == null || command.name() == null || command.name().isBlank()) {
      throw new IllegalArgumentException("名称不能为空");
    }
    if (!command.unlimited() && command.remainQuota() < 0) {
      throw new IllegalArgumentException("额度必须为非负数");
    }
    long expiredTime = command.expiredTime() == null ? -1L : command.expiredTime();
    String modelLimits = String.join(",", command.models());
    String allowIps = String.join("\n", command.ips());
    NewApiCreateTokenRequest body =
        new NewApiCreateTokenRequest(
            command.name(),
            command.unlimited() ? 0L : command.remainQuota(),
            command.unlimited(),
            expiredTime,
            !command.models().isEmpty(),
            modelLimits,
            allowIps);
    NewApiOperation op = new NewApiOperation("token-create", HttpMethod.POST, "/api/token/", true);
    try {
      NewApiRawResponse<Map<String, Object>> raw =
          exchange.executeRaw(
              op, body, authentication(session), new TypeReference<NewApiEnvelope<Map<String, Object>>>() {});
      if (raw.status() == 401) {
        throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
      }
      if (raw.status() == 400) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
      }
      if (raw.status() == 409) {
        throw new PortalException(PortalErrorCode.API_KEY_LIMIT_REACHED);
      }
      throwIfStatus(raw);
    } catch (UpstreamException e) {
      throw writeFailure(e);
    }
  }

  public void updateToken(NewApiSession session, long id, NewApiUpdateTokenCommand command) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    if (command == null) {
      throw new IllegalArgumentException("更新命令不能为空");
    }
    NewApiToken current = getToken(session, id);
    String name = command.name() != null ? command.name() : current.name();
    boolean unlimited = command.unlimited() != null ? command.unlimited() : Boolean.TRUE.equals(current.unlimitedQuota());
    long remainQuota = command.remainQuota() != null ? command.remainQuota()
        : (current.remainQuota() == null ? 0L : current.remainQuota());
    long expiredTime = command.expiredTime() != null ? command.expiredTime()
        : (current.expiredTime() == null ? -1L : current.expiredTime());
    String modelLimits = command.models() != null ? String.join(",", command.models())
        : (current.modelLimits() == null ? "" : current.modelLimits());
    String allowIps = command.ips() != null ? String.join("\n", command.ips())
        : (current.allowIps() == null ? "" : current.allowIps());
    NewApiToken merged =
        new NewApiToken(
            current.id(),
            current.userId(),
            current.key(),
            current.status(),
            name,
            current.createdTime(),
            current.accessedTime(),
            expiredTime,
            unlimited ? 0L : remainQuota,
            unlimited,
            !modelLimits.isEmpty(),
            modelLimits,
            allowIps,
            current.usedQuota(),
            current.group(),
            current.crossGroupRetry());
    NewApiOperation op = new NewApiOperation("token-update", HttpMethod.PUT, "/api/token/", true);
    try {
      NewApiRawResponse<Map<String, Object>> raw =
          exchange.executeRaw(
              op, merged, authentication(session), new TypeReference<NewApiEnvelope<Map<String, Object>>>() {});
      if (raw.status() == 401) {
        throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
      }
      if (raw.status() == 404) {
        throw new PortalException(PortalErrorCode.NOT_FOUND);
      }
      if (raw.status() == 400) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
      }
      throwIfStatus(raw);
    } catch (UpstreamException e) {
      throw writeFailure(e);
    }
  }

  public void updateStatus(NewApiSession session, long id, boolean enabled) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    NewApiStatusUpdateRequest body = new NewApiStatusUpdateRequest(id, enabled ? 1 : 2);
    NewApiOperation op =
        new NewApiOperation("token-status", HttpMethod.PUT, "/api/token/?status_only=true", true);
    try {
      NewApiRawResponse<Map<String, Object>> raw =
          exchange.executeRaw(
              op, body, authentication(session), new TypeReference<NewApiEnvelope<Map<String, Object>>>() {});
      if (raw.status() == 401) {
        throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
      }
      if (raw.status() == 404) {
        throw new PortalException(PortalErrorCode.NOT_FOUND);
      }
      if (raw.status() == 400) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
      }
      if (!raw.success() || raw.status() < 200 || raw.status() >= 300) {
        if (raw.status() >= 200 && raw.status() < 300 && !raw.success()) {
          throw new PortalException(PortalErrorCode.RESOURCE_CONFLICT);
        }
        throw exchange.failure(raw);
      }
    } catch (UpstreamException e) {
      throw writeFailure(e);
    }
  }

  public void deleteToken(NewApiSession session, long id) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    NewApiOperation op = new NewApiOperation("token-delete", HttpMethod.DELETE, "/api/token/" + id, true);
    try {
      NewApiRawResponse<Map<String, Object>> raw =
          exchange.executeRaw(
              op, null, authentication(session), new TypeReference<NewApiEnvelope<Map<String, Object>>>() {});
      throwIfStatus(raw);
    } catch (UpstreamException e) {
      throw writeFailure(e);
    }
  }

  public SensitiveSecret revealToken(NewApiSession session, long id) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    NewApiOperation op = new NewApiOperation("token-reveal", HttpMethod.POST, "/api/token/" + id + "/key", true);
    NewApiRawResponse<NewApiTokenKeyResponse> raw =
        exchange.executeRaw(
            op, null, authentication(session), new TypeReference<NewApiEnvelope<NewApiTokenKeyResponse>>() {});
    throwIfStatus(raw);
    if (raw.data() == null || raw.data().key() == null || raw.data().key().isBlank()) {
      throw exchange.failure(raw);
    }
    String normalized = NewApiTokenMapper.normalizeSecret(raw.data().key());
    return SensitiveSecret.of(normalized);
  }

  private RuntimeException writeFailure(UpstreamException e) {
    if (e.errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT
        || e.errorCode() == PortalErrorCode.UPSTREAM_UNAVAILABLE) {
      return new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN);
    }
    return e;
  }

  private void throwIfStatus(NewApiRawResponse<?> raw) {
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() == 404) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success()) {
      throw exchange.failure(raw);
    }
  }

  private void checkPage(int page, int pageSize) {
    if (page < 1) {
      throw new IllegalArgumentException("page 必须从 1 开始");
    }
    if (pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("pageSize 必须在 1～100 之间");
    }
  }

  private Map<String, String> authentication(NewApiSession session) {
    if (session == null || session.value() == null || session.value().isBlank() || session.userId() <= 0) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    return Map.of("Cookie", "session=" + session.value(), "New-Api-User", Long.toString(session.userId()));
  }
}
