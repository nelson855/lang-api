package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.web.account.aggregation.AccountConsumptionSnapshotService;
import com.lang.portal.web.dashboard.DashboardStatsQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/** 生产聚合调用链 + 受控 HTTP 来源；注册表不代表运行容器指标。 */
class AggregationObservabilityChainTests extends NewApiContractTestBase {
  private static final java.util.List<java.util.Map<String, Object>> EVIDENCE = new java.util.ArrayList<>();

  @org.junit.jupiter.api.AfterEach
  void captureSafeMetrics(org.junit.jupiter.api.TestInfo info) {
    var readings = new java.util.ArrayList<java.util.Map<String, Object>>();
    for (var meter : registry.getMeters()) {
      assertThat(meter.getId().getName()).isIn(
          "portal.aggregation.duration", "portal.aggregation.upstream.calls",
          "portal.aggregation.upstream.http-attempts", "portal.aggregation.upstream.pages",
          "portal.aggregation.upstream.records", "portal.aggregation.cache", "portal.aggregation.rejections");
      var tags = new java.util.TreeMap<String, String>();
      for (var tag : meter.getId().getTags()) {
        assertThat(tag.getKey()).isIn("operation", "outcome", "source", "cacheOutcome", "reason");
        assertThat(tag.getValue()).isIn("dashboard-stats", "account-consumption-snapshot",
            "success", "failure", "success-log", "error-log", "hit", "miss", "coalesced",
            "range", "pages", "records", "deadline", "single-timeout", "inconsistent-page");
        tags.put(tag.getKey(), tag.getValue());
      }
      double value = meter instanceof io.micrometer.core.instrument.Counter counter
          ? counter.count() : ((io.micrometer.core.instrument.Timer) meter).count();
      readings.add(java.util.Map.of("metric", meter.getId().getName(), "tags", tags, "count", value));
    }
    EVIDENCE.add(java.util.Map.of("scenario", info.getTestMethod().orElseThrow().getName(),
        "operation", registry.getMeters().stream().map(m -> m.getId().getTag("operation"))
            .filter(java.util.Objects::nonNull).findFirst().orElseThrow(),
        "httpReceived", server.getRequestCount(), "metrics", readings));
    registry.close();
  }

  @org.junit.jupiter.api.AfterAll
  static void persistSafeMetrics() throws java.io.IOException {
    var path = java.nio.file.Path.of("target/p210-observability.json");
    java.nio.file.Files.createDirectories(path.getParent());
    new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
        path.toFile(), java.util.Map.of("observationSource", "controlled-http-and-process-registry",
            "scenarios", EVIDENCE));
  }
  private final PortalCommonProperties props = new PortalCommonProperties();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AggregationMetrics metrics = new AggregationMetrics(registry);
  private Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
  private final NewApiSession session = new NewApiSession("controlled-session", 42L);

  private final org.springframework.http.client.SimpleClientHttpRequestFactory factory =
      new org.springframework.http.client.SimpleClientHttpRequestFactory();
  private Runnable afterResponse = () -> {};

  private Runnable query(boolean dashboard, boolean wide) {
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiLogClient client = new NewApiLogClient(new NewApiExchange(
        RestClient.builder().requestFactory(factory).requestInterceptor((request, body, execution) -> {
          var response = execution.execute(request, body);
          afterResponse.run();
          return response;
        }).build(), props, new NewApiErrorTranslator(), metrics));
    String end = wide ? "2026-10-01T00:00:00Z" : "2026-09-02T00:00:00Z";
    if (dashboard) {
      var service = new DashboardStatsQueryService(client, props, metrics);
      return () -> service.query(session, "42", "2026-09-01T00:00:00Z", end, "UTC", "DAY", clock);
    }
    var service = new AccountConsumptionSnapshotService(client, props, metrics);
    var context = AggregationQueryContext.of("2026-09-01T00:00:00Z", end, "UTC",
        AggregationGranularity.DAY, props.aggregation().baselineVersion(), props.aggregation());
    return () -> service.loadSnapshot(session, context, clock);
  }

  private String operation(boolean dashboard) {
    return dashboard ? "dashboard-stats" : "account-consumption-snapshot";
  }

  private double count(String name, String... tags) {
    var counter = registry.find("portal.aggregation." + name).tags(tags).counter();
    return counter == null ? 0 : counter.count();
  }

  private MockResponse page(int total, int offset) {
    return json("{\"success\":true,\"data\":{\"total\":" + total
        + ",\"items\":[{\"created_time\":" + (1788220800L + offset)
        + ",\"type\":2,\"model_name\":\"controlled-model\",\"quota\":1,"
        + "\"prompt_tokens\":1,\"completion_tokens\":1,\"use_time\":1,\"is_stream\":false}]}}");
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void thirtyDaysRejectsOnceWithoutHttp(boolean dashboard) {
    Runnable query = query(dashboard, true);
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isZero();
    assertThat(httpAttempts()).isZero();
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "range")).isEqualTo(1);
    assertThat(count("upstream.calls", "source", "success-log", "outcome", "failure")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isZero();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void failedSecondPageKeepsAcceptedFirstPageAndCanRecover(boolean dashboard) {
    props.aggregation().setPageSize(1);
    Runnable query = query(dashboard, false);
    server.enqueue(page(2, 0));
    server.enqueue(new MockResponse().setResponseCode(500));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isEqualTo(2);
    assertThat(httpAttempts()).isEqualTo(2);
    assertThat(count("upstream.calls", "source", "success-log", "outcome", "failure")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isEqualTo(1);
    assertThat(count("upstream.records", "source", "success-log", "outcome", "failure")).isEqualTo(1);
    server.enqueue(page(1, 0));
    query.run();
    query.run();
    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(httpAttempts()).isEqualTo(3);
    assertThat(count("upstream.calls", "source", "success-log", "outcome", "success")).isEqualTo(1);
    assertThat(count("cache", "operation", operation(dashboard), "cacheOutcome", "hit")).isEqualTo(1);
    assertThat(registry.get("portal.aggregation.duration").tags("operation", operation(dashboard),
        "outcome", "failure").timer().count()).isEqualTo(1);
    assertThat(registry.get("portal.aggregation.duration").tags("operation", operation(dashboard),
        "outcome", "success").timer().count()).isEqualTo(dashboard ? 1 : 2);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void pageLimitRejectsBeforeNextHttp(boolean dashboard) {
    props.aggregation().setPageSize(1);
    props.aggregation().setMaxPages(1);
    Runnable query = query(dashboard, false);
    server.enqueue(page(2, 0));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(httpAttempts()).isEqualTo(1);
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "pages")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isEqualTo(1);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void recordLimitDoesNotAcceptFetchedPage(boolean dashboard) {
    props.aggregation().setPageSize(1);
    props.aggregation().setMaxRecords(1);
    Runnable query = query(dashboard, false);
    server.enqueue(page(2, 0));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(httpAttempts()).isEqualTo(1);
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "records")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isZero();
    assertThat(count("upstream.records", "source", "success-log", "outcome", "failure")).isZero();
  }

  private double httpAttempts() {
    return registry.find("portal.aggregation.upstream.http-attempts").counters().stream()
        .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void singleHttpTimeoutIsCountedAndNotCached(boolean dashboard) {
    factory.setReadTimeout(100);
    Runnable query = query(dashboard, false);
    server.enqueue(page(1, 0).setHeadersDelay(500, TimeUnit.MILLISECONDS));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(httpAttempts()).isEqualTo(1);
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "single-timeout")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isZero();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void totalDeadlineRejectsBeforeSecondHttp(boolean dashboard) {
    props.aggregation().setPageSize(1);
    var mutable = new AggregationReadBudgetTimeoutTests.MutableClock(clock.instant());
    clock = mutable;
    afterResponse = () -> mutable.advance(java.time.Duration.ofSeconds(31));
    Runnable query = query(dashboard, false);
    server.enqueue(page(2, 0));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(httpAttempts()).isEqualTo(1);
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "deadline")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isEqualTo(1);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void inconsistentPageIsNotAccepted(boolean dashboard) {
    props.aggregation().setPageSize(1);
    Runnable query = query(dashboard, false);
    server.enqueue(page(2, 0));
    server.enqueue(page(3, 1));
    assertThatThrownBy(query::run).isInstanceOf(RuntimeException.class);
    assertThat(httpAttempts()).isEqualTo(2);
    assertThat(count("rejections", "operation", operation(dashboard), "reason", "inconsistent-page")).isEqualTo(1);
    assertThat(count("upstream.pages", "source", "success-log", "outcome", "failure")).isEqualTo(1);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void concurrentQueriesShareOneReadWithoutDuplicatingMetrics(boolean dashboard) throws Exception {
    Runnable query = query(dashboard, false);
    server.enqueue(page(1, 0).setHeadersDelay(500, TimeUnit.MILLISECONDS));
    var barrier = new java.util.concurrent.CyclicBarrier(10);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(10)) {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 10; i++) {
        futures.add(executor.submit(() -> {
          barrier.await(5, TimeUnit.SECONDS);
          query.run();
          return null;
        }));
      }
      for (var future : futures) future.get(5, TimeUnit.SECONDS);
    }
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(httpAttempts()).isEqualTo(1);
    assertThat(count("upstream.calls", "source", "success-log", "outcome", "success")).isEqualTo(1);
    assertThat(count("cache", "operation", operation(dashboard), "cacheOutcome", "miss")).isEqualTo(1);
    assertThat(count("cache", "operation", operation(dashboard), "cacheOutcome", "coalesced")
        + count("cache", "operation", operation(dashboard), "cacheOutcome", "hit")).isEqualTo(9);
  }
}
