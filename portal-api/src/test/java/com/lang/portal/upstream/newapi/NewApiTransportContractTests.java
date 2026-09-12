package com.lang.portal.upstream.newapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.RecordedRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

class NewApiTransportContractTests extends NewApiContractTestBase {

  private NewApiExchange exchange() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    return new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
  }

  @Test
  void successReturnsDataAndSendsWhitelistHeaders() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":1},\"extra\":\"x\"}"));
    NewApiOperation op = new NewApiOperation("probe", HttpMethod.GET, "/api/status", false);
    var data = exchange().execute(op, null, new TypeReference<NewApiEnvelope<java.util.Map<String, Object>>>() {});
    assertThat(data).containsEntry("id", 1);
    assertThat(data).doesNotContainKey("extra");
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/status");
    assertThat(request.getHeader("X-Request-Id")).isNotBlank();
    assertThat(request.getHeader("Authorization")).isNull();
  }

  @Test
  void businessFailureMapsToUpstreamError() {
    server.enqueue(businessFailure());
    NewApiOperation op = new NewApiOperation("probe", HttpMethod.GET, "/api/status", false);
    assertThatThrownBy(() -> exchange()
            .execute(op, null, new TypeReference<NewApiEnvelope<java.util.Map<String, Object>>>() {}))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void nonJsonMapsToUpstreamError() {
    server.enqueue(nonJson());
    NewApiOperation op = new NewApiOperation("probe", HttpMethod.GET, "/api/status", false);
    assertThatThrownBy(() -> exchange()
            .execute(op, null, new TypeReference<NewApiEnvelope<java.util.Map<String, Object>>>() {}))
        .isInstanceOf(UpstreamException.class);
  }

  @Test
  void disconnectSendsOnlyOneRequest() throws Exception {
    server.enqueue(disconnect());
    NewApiOperation op = new NewApiOperation("create", HttpMethod.POST, "/api/items", false);
    try {
      exchange().execute(op, Map.of("a", 1), new TypeReference<NewApiEnvelope<java.util.Map<String, Object>>>() {});
    } catch (UpstreamException ignored) {
    }
    assertThat(server.getRequestCount()).isEqualTo(1);
  }
}
