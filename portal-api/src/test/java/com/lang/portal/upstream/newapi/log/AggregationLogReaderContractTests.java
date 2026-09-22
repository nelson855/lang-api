package com.lang.portal.upstream.newapi.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AggregationLogReaderContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
  private static final long START_TS = 1788220800L;
  private static final long END_TS = 1788228000L;

  private NewApiLogClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiLogClient(exchange);
  }

  private AggregationQueryContext context() {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00Z",
        "2026-09-01T02:00:00Z",
        "UTC",
        AggregationGranularity.HOUR,
        "p2-2026-09-22-a",
        new PortalCommonProperties.Aggregation());
  }

  private AggregationReadBudget budget() {
    return new AggregationReadBudget(10, 200, FIXED.instant().plusSeconds(30), FIXED);
  }

  private static String item(long createdTime, int type, String requestId, long tokenId) {
    return "{\"created_time\":"
        + createdTime
        + ",\"type\":"
        + type
        + ",\"token_name\":\"probe-key-01\","
        + "\"model_name\":\"gpt-test\",\"quota\":500,\"prompt_tokens\":10,\"completion_tokens\":20,"
        + "\"use_time\":3,\"is_stream\":false,\"request_id\":\""
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
  void multiPageReadFixesEndTimeAndAdvancesPages() throws Exception {
    server.enqueue(
        json(pageBody(1, 3, item(START_TS, 2, "req-1", 9) + "," + item(START_TS + 100, 2, "req-2", 9))));
    server.enqueue(json(pageBody(2, 3, item(START_TS + 200, 2, "req-3", 10))));

    List<AggregationLogRecord> records =
        AggregationLogReader.readSuccessLogs(
            SESSION, context(), null, null, budget(), 2, Duration.ofHours(168), client());

    assertThat(records).hasSize(3);
    assertThat(records.get(0).requestId()).isEqualTo("req-1");
    assertThat(records.get(0).tokenId()).isEqualTo(9L);

    RecordedRequest first = takeRequest();
    RecordedRequest second = takeRequest();
    assertThat(first.getPath()).contains("page=1").contains("type=2");
    assertThat(second.getPath()).contains("page=2").contains("type=2");
    assertThat(first.getPath()).contains("end_timestamp=" + (END_TS - 1));
    assertThat(second.getPath()).contains("end_timestamp=" + (END_TS - 1));
  }

  @Test
  void outOfRangeRecordFailsAsContractError() {
    server.enqueue(json(pageBody(1, 1, item(END_TS, 2, "req-late", 9))));

    assertThatThrownBy(
            () ->
                AggregationLogReader.readSuccessLogs(
                    SESSION, context(), null, null, budget(), 2, Duration.ofHours(168), client()))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void secondPageFailureReturnsNoPartialResult() {
    server.enqueue(json(pageBody(1, 3, item(START_TS, 2, "req-1", 9) + "," + item(START_TS + 100, 2, "req-2", 9))));
    server.enqueue(businessFailure());

    assertThatThrownBy(
            () ->
                AggregationLogReader.readSuccessLogs(
                    SESSION, context(), null, null, budget(), 2, Duration.ofHours(168), client()))
        .isInstanceOf(UpstreamException.class);
  }

  @Test
  void liveRangeBeyondEvidenceGateIsRejectedBeforeUpstream() {
    AggregationQueryContext wide =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00Z",
            "2026-09-09T00:00:00Z",
            "UTC",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());

    assertThatThrownBy(
            () ->
                AggregationLogReader.readSuccessLogs(
                    SESSION, wide, null, null, budget(), 2, Duration.ofHours(168), client()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThat(server.getRequestCount()).isEqualTo(0);
  }

  @Test
  void successAndErrorShareOneBudget() throws Exception {
    AggregationReadBudget shared =
        new AggregationReadBudget(10, 2, FIXED.instant().plusSeconds(30), FIXED);
    server.enqueue(json(pageBody(1, 2, item(START_TS, 2, "req-1", 9) + "," + item(START_TS + 100, 2, "req-2", 9))));
    server.enqueue(
        json(
            pageBody(1, 1, item(START_TS + 200, 5, "req-3", 10).replace("\"type\":2", "\"type\":5"))));

    List<AggregationLogRecord> success =
        AggregationLogReader.readSuccessLogs(
            SESSION, context(), null, null, shared, 2, Duration.ofHours(168), client());
    assertThat(success).hasSize(2);
    assertThatThrownBy(
            () ->
                AggregationLogReader.readErrorLogs(
                    SESSION, context(), null, null, shared, 2, Duration.ofHours(168), client()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }
}
