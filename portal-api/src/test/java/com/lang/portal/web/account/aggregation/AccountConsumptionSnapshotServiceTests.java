package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AccountConsumptionSnapshotServiceTests {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private static final class StubClient extends NewApiLogClient {
    private final Deque<Object> script = new ArrayDeque<>();
    private final AtomicInteger successCalls = new AtomicInteger();

    StubClient() {
      super(null);
    }

    void enqueueSuccess(NewApiLogPage page) {
      script.add(page);
    }

    void enqueueFailure(RuntimeException failure) {
      script.add(failure);
    }

    int successCalls() {
      return successCalls.get();
    }

    @Override
    public NewApiLogPage listSuccess(NewApiSession session, NewApiLogQuery query) {
      successCalls.incrementAndGet();
      Object next = script.poll();
      if (next == null) {
        throw new IllegalStateException("未编排的上游调用");
      }
      if (next instanceof RuntimeException failure) {
        throw failure;
      }
      return (NewApiLogPage) next;
    }
  }

  private static PortalCommonProperties properties() {
    return new PortalCommonProperties();
  }

  private static AggregationQueryContext ctx(PortalCommonProperties properties) {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00Z",
        "2026-09-02T00:00:00Z",
        "UTC",
        AggregationGranularity.HOUR,
        properties.aggregation().baselineVersion(),
        properties.aggregation());
  }

  private static NewApiLogRecord rec(Instant at, String requestId, long quota) {
    return new NewApiLogRecord(at, "key", "gpt-4o", NewApiLogResult.SUCCESS,
        0L, 0L, 0L, false, quota, requestId, 1L);
  }

  @Test
  void loadsSnapshotOnceAndCachesItForSameKey() {
    StubClient client = new StubClient();
    client.enqueueSuccess(new NewApiLogPage(2, List.of(
        rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L),
        rec(Instant.parse("2026-09-01T01:30:00Z"), "r2", 20L))));

    PortalCommonProperties props = properties();
    AccountConsumptionSnapshotService service =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    ConsumptionSnapshot first = service.loadSnapshot(new NewApiSession("s", 42L), context, FIXED);
    ConsumptionSnapshot second = service.loadSnapshot(new NewApiSession("s", 42L), context, FIXED);

    assertThat(first.totalCount()).isEqualTo(2);
    assertThat(second).isSameAs(first);
    assertThat(client.successCalls()).isEqualTo(1);
  }

  @Test
  void differentUsersDoNotShareCacheEntries() {
    StubClient client = new StubClient();
    client.enqueueSuccess(new NewApiLogPage(1, List.of(rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L))));
    client.enqueueSuccess(new NewApiLogPage(1, List.of(rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L))));

    PortalCommonProperties props = properties();
    AccountConsumptionSnapshotService service =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    ConsumptionSnapshot a = service.loadSnapshot(new NewApiSession("s1", 1L), context, FIXED);
    ConsumptionSnapshot b = service.loadSnapshot(new NewApiSession("s2", 2L), context, FIXED);
    assertThat(a).isNotSameAs(b);
    assertThat(client.successCalls()).isEqualTo(2);
  }

  @Test
  void failedLoadIsNotCachedAndRetried() {
    StubClient client = new StubClient();
    client.enqueueFailure(new RuntimeException("boom"));
    client.enqueueSuccess(new NewApiLogPage(1, List.of(rec(Instant.parse("2026-09-01T00:30:00Z"), "r1", 10L))));

    PortalCommonProperties props = properties();
    AccountConsumptionSnapshotService service =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    NewApiSession session = new NewApiSession("s", 42L);
    try {
      service.loadSnapshot(session, context, FIXED);
    } catch (RuntimeException expected) {
      // ignored
    }
    ConsumptionSnapshot retried = service.loadSnapshot(session, context, FIXED);
    assertThat(retried.totalCount()).isEqualTo(1);
    assertThat(client.successCalls()).isEqualTo(2);
  }

  @Test
  void transactionViewKeysIncludeTypeAndPagination() {
    StubClient client = new StubClient();
    for (int i = 0; i < 4; i++) {
      client.enqueueSuccess(new NewApiLogPage(0, List.of()));
    }
    PortalCommonProperties props = properties();
    AccountConsumptionSnapshotService service =
        new AccountConsumptionSnapshotService(
            client, props, new AggregationMetrics(new SimpleMeterRegistry()));
    AggregationQueryContext context = ctx(props);
    NewApiSession session = new NewApiSession("s", 42L);
    ConsumptionSnapshot a = service.loadSnapshotForTransactions(session, context, null, 1, 20, FIXED);
    ConsumptionSnapshot b =
        service.loadSnapshotForTransactions(
            session, context, AccountTransactionType.CONSUMPTION, 1, 20, FIXED);
    ConsumptionSnapshot c = service.loadSnapshotForTransactions(session, context, null, 2, 20, FIXED);
    ConsumptionSnapshot d = service.loadSnapshotForTransactions(session, context, null, 1, 20, FIXED);
    assertThat(a).isNotSameAs(b);
    assertThat(a).isNotSameAs(c);
    assertThat(a).isSameAs(d);
  }
}
