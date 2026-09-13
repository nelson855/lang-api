package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenDeleteRevealContractTests extends NewApiContractTestBase {

  private NewApiTokenClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTokenClient(exchange);
  }

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  @Test
  void deleteSendsOnce() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));
    client().deleteToken(SESSION, 7L);

    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("DELETE");
    assertThat(request.getPath()).isEqualTo("/api/token/7");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void deleteMissingBecomesNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().deleteToken(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void deleteDisconnectBecomesResultUnknown() {
    server.enqueue(disconnect());
    assertThatThrownBy(() -> client().deleteToken(SESSION, 7L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void revealReturnsSensitiveSecretWithNormalizedPrefix() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":{\"key\":\"abc123\"}}"));
    SensitiveSecret secret = client().revealToken(SESSION, 7L);

    assertThat(secret.asString()).isEqualTo("sk-abc123");
    assertThat(secret.toString()).doesNotContain("abc123");
    assertThat(server.getRequestCount()).isEqualTo(1);
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/token/7/key");
  }

  @Test
  void revealDuplicatePrefixNormalizesOnce() {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":{\"key\":\"sk-sk-abc\"}}"));
    SensitiveSecret secret = client().revealToken(SESSION, 7L);
    assertThat(secret.asString()).isEqualTo("sk-abc");
    assertThat(secret.toString()).doesNotContain("sk-abc");
  }

  @Test
  void revealMissingKeyBecomesUpstreamError() {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":{}}"));
    assertThatThrownBy(() -> client().revealToken(SESSION, 7L))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void revealMissingBecomesNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().revealToken(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }
}
