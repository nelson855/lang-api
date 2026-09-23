package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.web.dashboard.DashboardStatsData;
import com.lang.portal.web.dashboard.DashboardStatsQueryService;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DashboardStatsVerticalTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC);
  private static final long START_TS = 1788220800L;

  private NewApiLogClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiLogClient(exchange);
  }

  private DashboardStatsQueryService service() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.aggregation().setPageSize(2);
    return new DashboardStatsQueryService(
        client(), props, new AggregationMetrics(new SimpleMeterRegistry()));
  }

  private static String item(long createdTime, String requestId, long tokenId, long useTime) {
    return "{\"created_time\":"
        + createdTime
        + ",\"type\":2,\"token_name\":\"probe-key-01\","
        + "\"model_name\":\"gpt-test\",\"quota\":500,\"prompt_tokens\":10,\"completion_tokens\":20,"
        + "\"use_time\":"
        + useTime
        + ",\"is_stream\":false,\"request_id\":\""
        + requestId
        + "\",\"token_id\":"
        + tokenId
        + "}";
  }

  private static String pageBody(int page, int total, String items) {
    return "{\"success\":true,\"message\":\"\",\"data\":{\"page\":"
        + page
        + ",\"page_size\":2,\"total\":"
        + total
        + ",\"items\":["
        + items
        + "]}}";
  }

  @Test
  void multiPageSuccessReadsOnceAndCacheHitsWithoutUpstream() {
    server.enqueue(
        json(pageBody(1, 3, item(START_TS, "req-1", 9, 3) + "," + item(START_TS + 100, "req-2", 9, 2))));
    server.enqueue(json(pageBody(2, 3, item(START_TS + 3600, "req-3", 10, 1))));

    DashboardStatsQueryService svc = service();
    DashboardStatsData first =
        svc.query(
            SESSION,
            "42",
            "2026-09-01T00:00:00Z",
            "2026-09-01T02:00:00Z",
            "UTC",
            "HOUR",
            FIXED);

    assertThat(first.metrics().tokenUsage().value()).isEqualTo(90L);
    assertThat(first.metrics().activeKeys().value()).isEqualTo(2L);
    assertThat(first.recentRequests().items()).hasSize(3);
    assertThat(server.getRequestCount()).isEqualTo(2);

    DashboardStatsData second =
        svc.query(
            SESSION,
            "42",
            "2026-09-01T00:00:00Z",
            "2026-09-01T02:00:00Z",
            "UTC",
            "HOUR",
            FIXED);
    assertThat(second).isSameAs(first);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void thirtyDayQueryRejectedBeforeUpstream() {
    DashboardStatsQueryService svc = service();

    assertThatThrownBy(
            () ->
                svc.query(
                    SESSION,
                    "42",
                    "2026-08-02T00:00:00Z",
                    "2026-09-01T00:00:00Z",
                    "UTC",
                    "DAY",
                    FIXED))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThat(server.getRequestCount()).isEqualTo(0);
  }

  @Test
  void upstreamFailureFailsWholeRequestWithoutPartialData() {
    server.enqueue(
        new okhttp3.mockwebserver.MockResponse().setResponseCode(500).setBody("boom"));

    DashboardStatsQueryService svc = service();
    assertThatThrownBy(
            () ->
                svc.query(
                    SESSION,
                    "42",
                    "2026-09-01T00:00:00Z",
                    "2026-09-01T02:00:00Z",
                    "UTC",
                    "HOUR",
                    FIXED))
        .isInstanceOf(RuntimeException.class);
  }
}
