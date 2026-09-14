package com.lang.portal.upstream.newapi.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiProfileUpdateClient {

  private static final NewApiOperation UPDATE =
      new NewApiOperation("profile-update", HttpMethod.PUT, "/api/user/self", true);

  private final NewApiExchange exchange;
  private final NewApiAuthenticationClient authClient;

  public NewApiProfileUpdateClient(NewApiExchange exchange, NewApiAuthenticationClient authClient) {
    this.exchange = exchange;
    this.authClient = authClient;
  }

  public NewApiUserProfile update(NewApiSession session, ProfileUpdateCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("更新命令不能为空");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", command.username());
    body.put("display_name", command.displayName());
    body.put("original_password", command.currentPassword());
    body.put("password", command.newPassword() == null ? "" : command.newPassword());

    NewApiRawResponse<JsonNode> raw;
    try {
      raw =
          exchange.executeRaw(
              UPDATE,
              body,
              authentication(session),
              new TypeReference<NewApiEnvelope<JsonNode>>() {});
    } catch (PortalException e) {
      if (e.errorCode() == PortalErrorCode.UNAUTHENTICATED) {
        throw e;
      }
      throw new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN);
    }
    if (raw.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    if (raw.status() < 200 || raw.status() >= 300) {
      throw new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN);
    }
    if (!raw.success()) {
      throw mapBusinessFailure(raw);
    }
    try {
      return authClient.currentUser(session);
    } catch (PortalException e) {
      throw new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN);
    }
  }

  private PortalException mapBusinessFailure(NewApiRawResponse<?> raw) {
    String message = raw.message() == null ? "" : raw.message();
    if (message.contains("原密码") || message.contains("密码错误") || message.contains("密码不正确")) {
      return new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    if ((message.contains("用户名") || message.contains("username"))
        && (message.contains("占用") || message.contains("已存在") || message.contains("重复"))) {
      return new PortalException(PortalErrorCode.RESOURCE_CONFLICT);
    }
    return exchange.failure(raw);
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
