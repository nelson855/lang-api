package com.lang.portal.upstream.newapi.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiHourlyClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiHourlyClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiHourlyClient(exchange);
  }

  @Test
  void hourlyUsesFrozenDataPath() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":["
            + "{\"hour\":1789030800,\"model_name\":\"gpt-test\",\"request_count\":5,"
            + "\"token_count\":100,\"quota\":500,\"futureField\":\"trim-me\"}]}"));
    NewApiHourlyQuery query = NewApiHourlyQuery.fromRange(
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T09:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC)));

    java.util.List<NewApiHourlyRow> rows = client().fetch(SESSION, query);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).requestCount()).isEqualTo(5L);
    assertThat(rows.get(0).tokenCount()).isEqualTo(100L);
    assertThat(rows.get(0).quota()).isEqualTo(500L);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).startsWith("/api/data/self");
    assertThat(request.getPath()).contains("start_timestamp=1789030800");
    assertThat(request.getPath()).contains("end_timestamp=1789037999");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }
}
