package com.lang.portal.upstream.newapi.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiSummaryClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiSummaryClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiSummaryClient(exchange);
  }

  @Test
  void summaryUsesFrozenStatPath() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":"
            + "{\"quota\":1200,\"rpm\":30,\"tpm\":4000,\"futureField\":\"trim-me\"}}"));
    NewApiSummaryQuery query = NewApiSummaryQuery.fromRange(
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T10:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC)));

    NewApiSummary summary = client().fetch(SESSION, query);

    assertThat(summary.quota()).isEqualTo(1200L);
    assertThat(summary.rpm()).isEqualTo(30L);
    assertThat(summary.tpm()).isEqualTo(4000L);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).startsWith("/api/log/self/stat");
    assertThat(request.getPath()).contains("start_timestamp=1789034400");
    assertThat(request.getPath()).contains("end_timestamp=1789037999");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void zeroValuesAreKeptNotTreatedAsMissing() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{\"quota\":0,\"rpm\":0,\"tpm\":0}}"));
    NewApiSummaryQuery query = NewApiSummaryQuery.fromRange(
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T10:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC)));

    NewApiSummary summary = client().fetch(SESSION, query);

    assertThat(summary.quota()).isZero();
    assertThat(summary.rpm()).isZero();
    assertThat(summary.tpm()).isZero();
  }

  @Test
  void missingFieldBecomesUpstreamError() {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":{\"quota\":1,\"rpm\":2}}"));
    NewApiSummaryQuery query = NewApiSummaryQuery.fromRange(
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T10:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC)));

    assertThatThrownBy(() -> client().fetch(SESSION, query))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void businessFailureBecomesUpstreamError() {
    server.enqueue(businessFailure());
    NewApiSummaryQuery query = NewApiSummaryQuery.fromRange(
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T10:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC)));

    assertThatThrownBy(() -> client().fetch(SESSION, query))
        .isInstanceOf(UpstreamException.class);
  }
}
