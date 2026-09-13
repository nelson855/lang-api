package com.lang.portal.upstream.newapi.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NewApiAuthenticationClient {

  private static final Logger log = LoggerFactory.getLogger(NewApiAuthenticationClient.class);

  private static final NewApiOperation REGISTER = new NewApiOperation(
      "register", HttpMethod.POST, "/api/user/register", false);
  private static final NewApiOperation LOGIN = new NewApiOperation(
      "login", HttpMethod.POST, "/api/user/login", false);
  private static final NewApiOperation CURRENT_USER = new NewApiOperation(
      "current_user", HttpMethod.GET, "/api/user/self", true);
  private static final NewApiOperation LOGOUT = new NewApiOperation(
      "logout", HttpMethod.GET, "/api/user/logout", true);

  private final NewApiExchange exchange;

  public NewApiAuthenticationClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public void register(NewApiCredentials credentials) {
    NewApiRawResponse<NewApiUserResponse> response = execute(REGISTER, credentials, Map.of());
    throwIfGenericFailure(response);
  }

  public NewApiLoginResult login(NewApiCredentials credentials) {
    NewApiRawResponse<NewApiUserResponse> response = execute(LOGIN, credentials, Map.of());
    if (response.status() >= 200 && response.status() < 300 && !response.success()) {
      log.warn("event=invalid_credentials");
      throw new PortalException(PortalErrorCode.INVALID_CREDENTIALS);
    }
    throwIfGenericFailure(response);
    if (!isActive(response.data())) {
      log.warn("event=invalid_credentials");
      throw new PortalException(PortalErrorCode.INVALID_CREDENTIALS);
    }
    NewApiUserProfile user = profile(response.data());
    String session = singleSessionCookie(response.setCookies());
    return new NewApiLoginResult(new NewApiSession(session, user.id()), user);
  }

  public NewApiUserProfile currentUser(NewApiSession session) {
    NewApiRawResponse<NewApiUserResponse> response = execute(CURRENT_USER, null, authentication(session));
    if (response.status() == 401) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    throwIfGenericFailure(response);
    if (!isActive(response.data())) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    NewApiUserProfile user = profile(response.data());
    if (user.id() != session.userId()) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    return user;
  }

  public void logout(NewApiSession session) {
    NewApiRawResponse<NewApiUserResponse> response = execute(LOGOUT, null, authentication(session));
    if (response.status() == 401) {
      return;
    }
    throwIfGenericFailure(response);
  }

  private NewApiRawResponse<NewApiUserResponse> execute(
      NewApiOperation operation,
      Object requestBody,
      Map<String, String> authentication) {
    return exchange.executeRaw(operation, requestBody, authentication, new TypeReference<NewApiEnvelope<NewApiUserResponse>>() {});
  }

  private void throwIfGenericFailure(NewApiRawResponse<?> response) {
    if (response.status() < 200 || response.status() >= 300 || !response.success()) {
      throw exchange.failure(response);
    }
  }

  private NewApiUserProfile profile(NewApiUserResponse response) {
    if (response == null || response.id() == null || response.id() <= 0 || response.username() == null || response.username().isBlank()) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return new NewApiUserProfile(response.id(), response.username(), response.displayName(), response.email());
  }

  private boolean isActive(NewApiUserResponse response) {
    return response != null && (response.status() == null || response.status() == 1);
  }

  private String singleSessionCookie(java.util.List<String> setCookies) {
    String session = null;
    for (String setCookie : setCookies) {
      String pair = setCookie.split(";", 2)[0];
      int equals = pair.indexOf('=');
      if (equals <= 0 || !"session".equals(pair.substring(0, equals).trim()) || pair.substring(equals + 1).isBlank()) {
        continue;
      }
      if (session != null) {
        throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
      }
      session = pair.substring(equals + 1);
    }
    if (session == null) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return session;
  }

  private Map<String, String> authentication(NewApiSession session) {
    if (session == null || session.value() == null || session.value().isBlank() || session.userId() <= 0) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    return Map.of("Cookie", "session=" + session.value(), "New-Api-User", Long.toString(session.userId()));
  }
}
