package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.aggregation.AggregationMetrics;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardStatsQueryServiceTests {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC);
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private DashboardStatsQueryService service(
      NewApiLogClient client, PortalCommonProperties props) {
    return new DashboardStatsQueryService(
        client, props, new AggregationMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void singleSnapshotDerivesAllSectionsFromSameRecords() {
    NewApiLogClient client = mock(NewApiLogClient.class);
    NewApiLogRecord firstRecord =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:01:40Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            false,
            500L,
            "req-1",
            9L);
    NewApiLogRecord secondRecord =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T01:05:00Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            false,
            500L,
            "req-2",
            10L);
    when(client.listSuccess(any(), any()))
        .thenReturn(new NewApiLogPage(2, List.of(firstRecord, secondRecord)));

    DashboardStatsData data =
        service(client, new PortalCommonProperties())
            .query(
                SESSION,
                "42",
                "2026-09-01T00:00:00Z",
                "2026-09-01T02:00:00Z",
                "UTC",
                "HOUR",
                FIXED);

    assertThat(data.baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(data.metrics().tokenUsage().value()).isEqualTo(60L);
    assertThat(data.metrics().activeKeys().value()).isEqualTo(2L);
    assertThat(data.recentRequests().items()).hasSize(2);
    assertThat(data.range().timezone()).isEqualTo("UTC");
    verify(client, times(1)).listSuccess(any(), any());
    verifyNoMoreInteractions(client);
  }

  @Test
  void sameUserRepeatHitsCacheAndCrossUserIsolated() {
    NewApiLogClient client = mock(NewApiLogClient.class);
    NewApiLogRecord cached =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:01:40Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            false,
            500L,
            "req-1",
            9L);
    when(client.listSuccess(any(), any()))
        .thenReturn(new NewApiLogPage(1, List.of(cached)));

    DashboardStatsQueryService service = service(client, new PortalCommonProperties());
    DashboardStatsData first =
        service.query(
            SESSION, "42", "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR", FIXED);
    DashboardStatsData second =
        service.query(
            SESSION, "42", "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR", FIXED);
    assertThat(second).isSameAs(first);
    verify(client, times(1)).listSuccess(any(), any());

    NewApiSession other = new NewApiSession("other-session", 77L);
    service.query(
        other, "77", "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR", FIXED);
    verify(client, times(2)).listSuccess(any(), any());
  }

  @Test
  void failedLoadIsNotCachedAndCanRetry() {
    NewApiLogClient client = mock(NewApiLogClient.class);
    NewApiLogRecord retriedRecord =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:01:40Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            false,
            500L,
            "req-1",
            9L);
    when(client.listSuccess(any(), any()))
        .thenThrow(
            new com.lang.portal.base.exception.PortalException(
                com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_ERROR))
        .thenReturn(new NewApiLogPage(1, List.of(retriedRecord)));

    DashboardStatsQueryService service = service(client, new PortalCommonProperties());
    assertThatThrownBy(
            () ->
                service.query(
                    SESSION,
                    "42",
                    "2026-09-01T00:00:00Z",
                    "2026-09-01T02:00:00Z",
                    "UTC",
                    "HOUR",
                    FIXED))
        .isInstanceOf(com.lang.portal.base.exception.PortalException.class);
    DashboardStatsData retried =
        service.query(
            SESSION, "42", "2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR", FIXED);
    assertThat(retried.metrics().tokenUsage().value()).isEqualTo(30L);
    verify(client, times(2)).listSuccess(any(), any());
  }

  @Test
  void concurrentSameKeyLoadsOnce() throws Exception {
    NewApiLogClient client = mock(NewApiLogClient.class);
    NewApiLogRecord concurrentRecord =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:01:40Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            false,
            500L,
            "req-1",
            9L);
    when(client.listSuccess(any(), any()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(50);
              return new NewApiLogPage(1, List.of(concurrentRecord));
            });

    DashboardStatsQueryService service = service(client, new PortalCommonProperties());
    int threads = 8;
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(threads);
    try {
      java.util.List<java.util.concurrent.Future<DashboardStatsData>> futures =
          new java.util.ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () ->
                    service.query(
                        SESSION,
                        "42",
                        "2026-09-01T00:00:00Z",
                        "2026-09-01T02:00:00Z",
                        "UTC",
                        "HOUR",
                        FIXED)));
      }
      java.util.List<DashboardStatsData> results = new java.util.ArrayList<>();
      for (var f : futures) {
        results.add(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
      }
      for (DashboardStatsData r : results) {
        assertThat(r).isSameAs(results.get(0));
      }
      verify(client, times(1)).listSuccess(any(), any());
    } finally {
      pool.shutdownNow();
    }
  }
}
