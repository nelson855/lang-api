package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenStatusContractTests extends NewApiContractTestBase {

  private NewApiTokenClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTokenClient(exchange);
  }

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void disableSendsStatusOnlyUpdate() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));
    client().updateStatus(SESSION, 7L, false);

    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
    assertThat(request.getPath()).isEqualTo("/api/token/?status_only=true");
    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
    assertThat(body.get("id").asLong()).isEqualTo(7L);
    assertThat(body.get("status").asInt()).isEqualTo(2);
  }

  @Test
  void enableSendsStatusOne() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));
    client().updateStatus(SESSION, 7L, true);

    RecordedRequest request = takeRequest();
    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
    assertThat(body.get("status").asInt()).isEqualTo(1);
  }

  @Test
  void statusBusinessFailureBecomesConflict() {
    server.enqueue(businessFailure());
    assertThatThrownBy(() -> client().updateStatus(SESSION, 7L, true))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.RESOURCE_CONFLICT);
  }

  @Test
  void statusMissingBecomesNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().updateStatus(SESSION, 999L, false))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void statusUnauthorizedBecomesUnauthenticated() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().updateStatus(SESSION, 7L, false))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }
}
