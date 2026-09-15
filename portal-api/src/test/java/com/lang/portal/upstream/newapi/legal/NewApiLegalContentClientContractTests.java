package com.lang.portal.upstream.newapi.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiLegalContentClientContractTests extends NewApiContractTestBase {

  private NewApiLegalContentClient client() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange = new NewApiExchange(
        RestClient.builder().build(), properties, new NewApiErrorTranslator());
    return new NewApiLegalContentClient(exchange);
  }

  @Test
  void readsUserAgreementFromFrozenAnonymousGetContract() throws Exception {
    server.enqueue(json("""
        {"success":true,"message":"","data":"# 用户协议\\n\\n已脱敏正文"}
        """));

    assertThat(client().getUserAgreement()).isEqualTo("# 用户协议\n\n已脱敏正文");

    var request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user-agreement");
  }

  @Test
  void anonymousLegalRequestsUseOnlyThePublicHeaderPolicy() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":\"正文\"}"));

    client().getUserAgreement();

    var request = takeRequest();
    assertThat(request.getHeader("Accept")).isEqualTo("application/json");
    assertThat(request.getHeader("X-Request-Id")).isNotBlank();
    assertThat(request.getHeader("Content-Type")).isNull();
    assertThat(request.getHeader("Cookie")).isNull();
    assertThat(request.getHeader("New-Api-User")).isNull();
    assertThat(request.getHeader("Authorization")).isNull();
  }

  @Test
  void readsPrivacyPolicyFromFrozenAnonymousGetContract() throws Exception {
    server.enqueue(json("""
        {"success":true,"message":"","data":"# Privacy Policy\\n\\nSanitized fixture"}
        """));

    assertThat(client().getPrivacyPolicy()).isEqualTo("# Privacy Policy\n\nSanitized fixture");

    var request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/privacy-policy");
  }

  @Test
  void preservesAnEmptyStringBodyForPublicationValidation() {
    server.enqueue(json("{" + "\"success\":true,\"message\":\"\",\"data\":\"\"}"));

    assertThat(client().getUserAgreement()).isEmpty();
  }

  @Test
  void mapsBusinessFailureWithoutExposingUpstreamMessage() {
    server.enqueue(json("{" + "\"success\":false,\"message\":\"upstream legal message\",\"data\":null}"));

    assertThatThrownBy(() -> client().getPrivacyPolicy())
        .isInstanceOf(UpstreamException.class)
        .hasMessageNotContaining("upstream legal message");
  }

  @Test
  void rejectsNonStringData() {
    server.enqueue(json("{" + "\"success\":true,\"message\":\"\",\"data\":{\"html\":\"unexpected\"}}"));

    assertThatThrownBy(() -> client().getUserAgreement()).isInstanceOf(UpstreamException.class);
  }

  @Test
  void rejectsMissingData() {
    server.enqueue(json("{\"success\":true,\"message\":\"\"}"));

    assertThatThrownBy(() -> client().getPrivacyPolicy()).isInstanceOf(UpstreamException.class);
  }

  @Test
  void ignoresUnknownEnvelopeFields() {
    server.enqueue(json("""
        {"success":true,"message":"","data":"正文","version":"v0.13.2","brand":"forbidden"}
        """));

    assertThat(client().getPrivacyPolicy()).isEqualTo("正文");
  }
}
