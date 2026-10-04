package com.lang.portal.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingClient;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.web.catalog.CatalogData;
import com.lang.portal.web.catalog.CatalogModel;
import com.lang.portal.web.catalog.CatalogService;
import com.lang.portal.web.catalog.ModelProviderOption;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CatalogVerticalTests extends NewApiContractTestBase {

  private CatalogService service() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofSeconds(60));
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    NewApiPricingClient pricing = new NewApiPricingClient(exchange);
    return new CatalogService(pricing, props);
  }

  @Test
  void listDetailsProvidersShareSingleUpstreamLoad() throws Exception {
    server.enqueue(
        json(
            "{\"success\":true,\"message\":\"ok\",\"data\":{"
                + "\"pricing_version\":\"v-vertical\","
                + "\"data\":["
                + "{\"model_name\":\"b-model\",\"vendor_id\":1,\"quota_type\":1,\"model_price\":0.001},"
                + "{\"model_name\":\"a-model\",\"vendor_id\":1,\"quota_type\":1,\"model_price\":0.002}"
                + "],\"vendors\":[{\"id\":1,\"name\":\"Example\"}]}}"));

    CatalogService service = service();
    CatalogData list = service.current();
    var details = service.findDetails("a-model");
    var providers = service.providers();

    assertThat(list.pricingVersion()).isEqualTo("v-vertical");
    assertThat(list.models().stream().map(CatalogModel::id).toList())
        .containsExactly("a-model", "b-model");
    assertThat(details).isPresent();
    assertThat(details.orElseThrow().id()).isEqualTo("a-model");
    assertThat(details.orElseThrow().provider()).isEqualTo("Example");
    assertThat(details.orElseThrow().pricing())
        .isEqualTo(list.models().get(0).pricing());
    assertThat(service.snapshot().pricingVersion()).isEqualTo("v-vertical");
    assertThat(providers)
        .containsExactly(new ModelProviderOption("Example", "Example", 2));
    assertThat(server.getRequestCount()).isEqualTo(1);
  }
}
