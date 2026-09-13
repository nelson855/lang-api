package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenCreateContractTests extends NewApiContractTestBase {

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
  void limitedCreateConvertsFieldsAndSendsOnce() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));

    var command =
        new NewApiCreateTokenCommand(
            "probe-key-01", false, 100000L, 1790000000L, List.of("gpt-4o", "claude-sonnet"), List.of("1.1.1.1", "::1"));
    client().createToken(SESSION, command);

    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/token/");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");

    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
    assertThat(body.get("name").asText()).isEqualTo("probe-key-01");
    assertThat(body.get("unlimited_quota").asBoolean()).isFalse();
    assertThat(body.get("remain_quota").asLong()).isEqualTo(100000L);
    assertThat(body.get("expired_time").asLong()).isEqualTo(1790000000L);
    assertThat(body.get("model_limits_enabled").asBoolean()).isTrue();
    assertThat(body.get("model_limits").asText()).isEqualTo("gpt-4o,claude-sonnet");
    assertThat(body.get("allow_ips").asText()).isEqualTo("1.1.1.1\n::1");
    assertThat(body.has("id")).isFalse();
    assertThat(body.has("key")).isFalse();
  }

  @Test
  void unlimitedNeverExpireUsesMinusOne() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));

    var command = new NewApiCreateTokenCommand("unlimited", true, 0L, null, List.of(), List.of());
    client().createToken(SESSION, command);

    RecordedRequest request = takeRequest();
    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
    assertThat(body.get("unlimited_quota").asBoolean()).isTrue();
    assertThat(body.get("expired_time").asLong()).isEqualTo(-1L);
    assertThat(body.get("model_limits_enabled").asBoolean()).isFalse();
  }

  @Test
  void businessFailureBecomesUpstreamErrorAndSendsOnce() {
    server.enqueue(businessFailure());
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void unauthorizedBecomesUnauthenticated() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false}"));
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void nonJsonBecomesUpstreamError() {
    server.enqueue(nonJson());
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(UpstreamException.class);
  }
}
