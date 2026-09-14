package com.lang.portal.upstream.newapi.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiPricingClientContractTests extends NewApiContractTestBase {

  private NewApiPricingClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange = new NewApiExchange(
        RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiPricingClient(exchange);
  }

  @Test
  void anonymousPricingSendsNoAuthHeaders() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":[]}"));
    client().fetchSnapshot();
    var req = takeRequest();
    assertThat(req.getPath()).isEqualTo("/api/pricing");
    assertThat(req.getMethod()).isEqualTo("GET");
    assertThat(req.getHeader("Cookie")).isNull();
    assertThat(req.getHeader("New-Api-User")).isNull();
    assertThat(req.getHeader("Authorization")).isNull();
  }

  @Test
  void businessFailureThrowsWithoutLeak() {
    server.enqueue(businessFailure());
    assertThatThrownBy(() -> client().fetchSnapshot()).isInstanceOf(UpstreamException.class);
  }

  @Test
  void illegalJsonThrowsUpstreamError() {
    server.enqueue(nonJson());
    assertThatThrownBy(() -> client().fetchSnapshot()).isInstanceOf(UpstreamException.class);
  }
}
