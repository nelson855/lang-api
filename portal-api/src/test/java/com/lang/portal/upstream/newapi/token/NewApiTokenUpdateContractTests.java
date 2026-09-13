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
import java.util.List;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenUpdateContractTests extends NewApiContractTestBase {

  private NewApiTokenClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTokenClient(exchange);
  }

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String fullToken() {
    return """
        {"success":true,"message":"","data":{"id":7,"user_id":42,"key":"fN95**********CMHQ",
          "status":1,"name":"probe-key-01","created_time":1789180807,"accessed_time":1789180807,
          "expired_time":-1,"remain_quota":100000,"unlimited_quota":false,
          "model_limits_enabled":true,"model_limits":"gpt-4o","allow_ips":"1.1.1.1",
          "used_quota":5,"group":"default","cross_group_retry":true,"DeletedAt":null}}
        """;
  }

  @Test
  void updateMergesAllowedFieldsAndPreservesInternalOnes() throws Exception {
    server.enqueue(json(fullToken()));
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));

    var command = new NewApiUpdateTokenCommand("renamed", null, null, null, List.of("claude-sonnet"), null);
    client().updateToken(SESSION, 7L, command);

    assertThat(server.getRequestCount()).isEqualTo(2);
    RecordedRequest read = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(read.getPath()).isEqualTo("/api/token/7");

    RecordedRequest write = takeRequest();
    assertThat(write.getMethod()).isEqualTo("PUT");
    assertThat(write.getPath()).isEqualTo("/api/token/");
    assertThat(write.getHeader("New-Api-User")).isEqualTo("42");

    JsonNode body = MAPPER.readTree(write.getBody().readUtf8());
    assertThat(body.get("name").asText()).isEqualTo("renamed");
    assertThat(body.get("model_limits").asText()).isEqualTo("claude-sonnet");
    assertThat(body.get("key").asText()).isEqualTo("fN95**********CMHQ");
    assertThat(body.get("status").asInt()).isEqualTo(1);
    assertThat(body.get("used_quota").asLong()).isEqualTo(5L);
    assertThat(body.get("group").asText()).isEqualTo("default");
    assertThat(body.get("allow_ips").asText()).isEqualTo("1.1.1.1");
  }

  @Test
  void updateMissingTokenBecomesNotFound() {
    server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    var command = new NewApiUpdateTokenCommand("renamed", null, null, null, null, null);
    assertThatThrownBy(() -> client().updateToken(SESSION, 999L, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void updateWriteDisconnectBecomesResultUnknown() {
    server.enqueue(json(fullToken()).addHeader("Connection", "close"));
    server.enqueue(disconnect());
    var command = new NewApiUpdateTokenCommand("renamed", null, null, null, null, null);
    assertThatThrownBy(() -> client().updateToken(SESSION, 7L, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }
}
