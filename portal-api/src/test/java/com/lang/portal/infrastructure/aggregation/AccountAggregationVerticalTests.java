package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.web.account.aggregation.AccountConsumptionSnapshotService;
import com.lang.portal.web.account.aggregation.AccountConsumptionSummaryData;
import com.lang.portal.web.account.aggregation.AccountReasonCode;
import com.lang.portal.web.account.aggregation.AccountAvailability;
import com.lang.portal.web.account.aggregation.AccountTransactionType;
import com.lang.portal.web.account.aggregation.AccountTransactionsData;
import com.lang.portal.web.account.aggregation.AccountTransactionsPaginator;
import com.lang.portal.web.account.aggregation.ConsumptionSnapshot;
import com.lang.portal.web.account.aggregation.ConsumptionSummaryCalculator;
import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AccountAggregationVerticalTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC);
  private static final long START_TS = 1788220800L;

  private PortalCommonProperties props() {
    PortalCommonProperties p = new PortalCommonProperties();
    p.upstream().newApi().setBaseUrl(baseUrl());
    return p;
  }

  private NewApiLogClient client(PortalCommonProperties props) {
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiLogClient(exchange);
  }

  private static String item(long createdTime, String requestId, long quota) {
    return "{\"created_time\":"
        + createdTime
        + ",\"type\":2,\"token_name\":\"probe-key-01\","
        + "\"model_name\":\"gpt-test\",\"quota\":"
        + quota
        + ",\"prompt_tokens\":10,\"completion_tokens\":20,"
        + "\"use_time\":1,\"is_stream\":false,\"request_id\":\""
        + requestId
        + "\",\"token_id\":9}";
  }

  private static String pageBody(int page, int total, String items) {
    return "{\"success\":true,\"message\":\"\",\"data\":{\"page\":"
        + page
        + ",\"page_size\":100,\"total\":"
        + total
        + ",\"items\":["
        + items
        + "]}}";
  }

  private AggregationQueryContext ctx(PortalCommonProperties props, String start, String end) {
    return AggregationQueryContext.of(
        start, end, "UTC", AggregationGranularity.HOUR,
        props.aggregation().baselineVersion(), props.aggregation());
  }

  @Test
  void summaryAndTransactionsShareSameSnapshotViaCache() {
    PortalCommonProperties props = props();
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(
        json(pageBody(1, 3,
            item(START_TS, "req-1", 10L) + ","
                + item(START_TS + 60, "req-2", 20L) + ","
                + item(START_TS + 120, "req-3", 30L))));

    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    ConsumptionSnapshot first = snapshots.loadSnapshot(SESSION, context, FIXED);
    AccountConsumptionSummaryData summary = ConsumptionSummaryCalculator.summarize(first, context);
    assertThat(summary.recordCount().value()).isEqualTo("3");
    assertThat(summary.quotaTotal().value()).isEqualTo("60");
    assertThat(summary.moneyTotal().availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(summary.moneyTotal().reasonCode())
        .isEqualTo(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
    assertThat(server.getRequestCount()).isEqualTo(1);

    // 流水请求命中同一缓存（filters=Map.of()）
    ConsumptionSnapshot second = snapshots.loadSnapshot(SESSION, context, FIXED);
    assertThat(second).isSameAs(first);
    assertThat(server.getRequestCount()).isEqualTo(1);

    AccountTransactionsData page1 =
        AccountTransactionsPaginator.paginate(
            second, context, SESSION.userId(), AccountTransactionType.CONSUMPTION, 1, 2, 200);
    AccountTransactionsData page2 =
        AccountTransactionsPaginator.paginate(
            second, context, SESSION.userId(), AccountTransactionType.CONSUMPTION, 2, 2, 200);
    assertThat(page1.total()).isEqualTo(3);
    assertThat(page2.total()).isEqualTo(3);
    assertThat(page1.items()).hasSize(2);
    assertThat(page2.items()).hasSize(1);
  }

  @Test
  void upstreamFailurePropagatesAndIsNotCached() {
    PortalCommonProperties props = props();
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(500));
    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    assertThatThrownBy(() -> snapshots.loadSnapshot(SESSION, context, FIXED))
        .isInstanceOf(RuntimeException.class);

    server.enqueue(json(pageBody(1, 0, "")));
    ConsumptionSnapshot retried = snapshots.loadSnapshot(SESSION, context, FIXED);
    assertThat(retried.totalCount()).isZero();
  }

  @Test
  void midPaginationTotalChangeFailsWithoutCaching() {
    PortalCommonProperties props = props();
    props.aggregation().setPageSize(2);
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(json(pageBody(1, 4, item(START_TS, "r1", 1L) + "," + item(START_TS + 1, "r2", 1L))));
    server.enqueue(json(pageBody(2, 99, item(START_TS + 2, "r3", 1L))));

    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    assertThatThrownBy(() -> snapshots.loadSnapshot(SESSION, context, FIXED))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void emptyPageBeforeTotalIsRejected() {
    PortalCommonProperties props = props();
    props.aggregation().setPageSize(2);
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(json(pageBody(1, 4, "")));
    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    assertThatThrownBy(() -> snapshots.loadSnapshot(SESSION, context, FIXED))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void duplicateTransactionIdInSnapshotFailsEntirely() {
    PortalCommonProperties props = props();
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(
        json(pageBody(1, 2, item(START_TS, "dup", 1L) + "," + item(START_TS + 1, "dup", 2L))));
    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    ConsumptionSnapshot snapshot = snapshots.loadSnapshot(SESSION, context, FIXED);
    assertThatThrownBy(
            () ->
                AccountTransactionsPaginator.paginate(
                    snapshot, context, SESSION.userId(), AccountTransactionType.CONSUMPTION, 1, 20, 200))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rangeBeyondSevenDaysRejectedBeforeUpstream() {
    PortalCommonProperties props = props();
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext tooWide =
        ctx(props, "2026-08-01T00:00:00Z", "2026-09-10T00:00:00Z");
    assertThatThrownBy(() -> snapshots.loadSnapshot(SESSION, tooWide, FIXED))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void onlyConsumptionSourceIsInvokedForAllTypes() {
    PortalCommonProperties props = props();
    NewApiLogClient logClient = client(props);
    AccountConsumptionSnapshotService snapshots =
        new AccountConsumptionSnapshotService(
            logClient, props, new AggregationMetrics(new SimpleMeterRegistry()));
    server.enqueue(json(pageBody(1, 0, "")));
    AggregationQueryContext context =
        ctx(props, "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z");
    snapshots.loadSnapshot(SESSION, context, FIXED);
    // 验证上游只调用了 /api/log/self，并未触发 topup/refund/admin 等其他路径
    try {
      RecordedRequestAssertions.assertOnlyLogSelf(server.takeRequest());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static final class RecordedRequestAssertions {
    static void assertOnlyLogSelf(okhttp3.mockwebserver.RecordedRequest request) {
      String path = request.getPath();
      org.assertj.core.api.Assertions.assertThat(path).startsWith("/api/log/self");
      org.assertj.core.api.Assertions.assertThat(path).contains("type=2");
      org.assertj.core.api.Assertions.assertThat(path).doesNotContain("topup");
      org.assertj.core.api.Assertions.assertThat(path).doesNotContain("admin");
    }
  }
}
