package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountAggregationFailureChainTests {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
  private static final NewApiSession SESSION = new NewApiSession("sess", 42L);

  private static final class StubClient extends NewApiLogClient {
    private final Deque<Object> script = new ArrayDeque<>();
    private int calls = 0;

    StubClient() {
      super(null);
    }

    void enqueue(NewApiLogPage page) {
      script.add(page);
    }

    void enqueue(RuntimeException failure) {
      script.add(failure);
    }

    int calls() {
      return calls;
    }

    @Override
    public NewApiLogPage listSuccess(NewApiSession session, NewApiLogQuery query) {
      calls++;
      Object next = script.poll();
      if (next == null) throw new IllegalStateException("未编排的上游");
      if (next instanceof RuntimeException failure) throw failure;
      return (NewApiLogPage) next;
    }
  }

  private static AggregationQueryContext ctx(PortalCommonProperties p) {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00Z",
        "2026-09-02T00:00:00Z",
        "UTC",
        AggregationGranularity.HOUR,
        p.aggregation().baselineVersion(),
        p.aggregation());
  }

  private static NewApiLogRecord rec(Instant at, String requestId, long quota) {
    return new NewApiLogRecord(at, "key", "gpt-4o", NewApiLogResult.SUCCESS,
        0L, 0L, 0L, false, quota, requestId, 1L);
  }

  @Test
  void totalChangesMidPaginationFails() {
    StubClient client = new StubClient();
    PortalCommonProperties props = new PortalCommonProperties();
    props.aggregation().setPageSize(2);
    client.enqueue(new NewApiLogPage(4, List.of(
        rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 1L),
        rec(Instant.parse("2026-09-01T01:30:00Z"), "r2", 1L))));
    client.enqueue(new NewApiLogPage(99, List.of(
        rec(Instant.parse("2026-09-01T02:30:00Z"), "r3", 1L))));

    AccountConsumptionSnapshotService svc =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    assertThatThrownBy(() -> svc.loadSnapshot(SESSION, ctx(props), FIXED))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void earlyEmptyPageWithPositiveTotalFails() {
    StubClient client = new StubClient();
    PortalCommonProperties props = new PortalCommonProperties();
    props.aggregation().setPageSize(2);
    client.enqueue(new NewApiLogPage(4, List.of()));

    AccountConsumptionSnapshotService svc =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    assertThatThrownBy(() -> svc.loadSnapshot(SESSION, ctx(props), FIXED))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void quotaOverflowFailsWithoutCaching() {
    StubClient client = new StubClient();
    PortalCommonProperties props = new PortalCommonProperties();
    client.enqueue(new NewApiLogPage(2, List.of(
        rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", Long.MAX_VALUE),
        rec(Instant.parse("2026-09-01T01:30:00Z"), "r2", 10L))));

    AccountConsumptionSnapshotService svc =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    assertThatThrownBy(() -> svc.loadSnapshot(SESSION, context, FIXED))
        .isInstanceOf(IllegalStateException.class);

    client.enqueue(new NewApiLogPage(0, List.of()));
    ConsumptionSnapshot retried = svc.loadSnapshot(SESSION, context, FIXED);
    assertThat(retried.totalCount()).isZero();
  }

  @Test
  void deepPaginationRejectedBeforeAnyUpstreamCall() {
    StubClient client = new StubClient();
    PortalCommonProperties props = new PortalCommonProperties();
    AccountConsumptionSnapshotService svc =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    ConsumptionSnapshot empty = ConsumptionSnapshot.from(List.of());
    assertThatThrownBy(
            () ->
                AccountTransactionsPaginator.paginate(
                    empty, context, 42L, AccountTransactionType.CONSUMPTION, 11, 20, 200))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThat(client.calls()).isZero();
  }

  @Test
  void duplicateRequestIdAcrossSnapshotTriggersFailure() {
    AggregationLogRecord a =
        new AggregationLogRecord(
            Instant.parse("2026-09-01T00:30:00Z"),
            AggregationLogResult.SUCCESS, null, "m", "dup", null, 0L, 0L, 0L, false, 1L);
    AggregationLogRecord b =
        new AggregationLogRecord(
            Instant.parse("2026-09-01T01:30:00Z"),
            AggregationLogResult.SUCCESS, null, "m", "dup", null, 0L, 0L, 0L, false, 2L);
    ConsumptionSnapshot snapshot = ConsumptionSnapshot.from(List.of(a, b));
    PortalCommonProperties props = new PortalCommonProperties();
    AggregationQueryContext context = ctx(props);
    assertThatThrownBy(
            () ->
                AccountTransactionsPaginator.paginate(
                    snapshot, context, 42L, AccountTransactionType.CONSUMPTION, 1, 20, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionId");
  }

  @Test
  void paginatorNeverReturnsPartialDataOnOverflow() {
    AggregationLogRecord r =
        new AggregationLogRecord(
            Instant.parse("2026-09-01T00:30:00Z"),
            AggregationLogResult.SUCCESS, null, "m", "r", null, 0L, 0L, 0L, false, Long.MAX_VALUE);
    AggregationLogRecord r2 =
        new AggregationLogRecord(
            Instant.parse("2026-09-01T01:30:00Z"),
            AggregationLogResult.SUCCESS, null, "m", "r2", null, 0L, 0L, 0L, false, 1L);
    assertThatThrownBy(() -> ConsumptionSnapshot.from(List.of(r, r2)))
        .isInstanceOf(IllegalStateException.class);
  }
}
