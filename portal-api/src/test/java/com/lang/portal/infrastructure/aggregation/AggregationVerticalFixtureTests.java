package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationAccumulator;
import com.lang.portal.base.aggregation.AggregationBucketPlan;
import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.aggregation.AggregationTotals;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.AggregationLogReader;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AggregationVerticalFixtureTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
  private static final long START_TS = 1788220800L;

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

  private String firstPageBody() {
    return pageBody(1, 3, item(START_TS, "req-1", 9, 3) + "," + item(START_TS + 100, "req-2", 9, 2));
  }

  private String secondPageBody() {
    return pageBody(2, 3, item(START_TS + 3600, "req-3", 10, 1));
  }

  @Test
  void fullChainFromQueryToCachedTotals() {
    server.enqueue(json(firstPageBody()));
    server.enqueue(json(secondPageBody()));

    AggregationQueryContext query = context();
    AggregationReadBudget budget =
        new AggregationReadBudget(10, 200, FIXED.instant().plusSeconds(30), FIXED);
    AggregationCache<AggregationTotals> cache =
        new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey key =
        AggregationCacheKey.of("usage-summary", "user-42", query, Map.of());
    AtomicInteger computations = new AtomicInteger();

    AggregationTotals first =
        cache.get(
            key,
            k -> {
              computations.incrementAndGet();
              List<AggregationLogRecord> records =
                  AggregationLogReader.readSuccessLogs(
                      SESSION, query, null, null, budget, 2, Duration.ofHours(168), client());
              List<AggregationBucketPlan.Bucket> buckets = AggregationBucketPlan.plan(query);
              assertThat(buckets).hasSize(2);
              AggregationAccumulator accumulator = new AggregationAccumulator();
              records.forEach(accumulator::add);
              return accumulator.totals();
            });

    assertThat(first.inputTokens()).isEqualTo(30L);
    assertThat(first.outputTokens()).isEqualTo(60L);
    assertThat(first.durationMs()).isEqualTo(6000L);
    assertThat(first.rawQuota()).isEqualByComparingTo(new java.math.BigDecimal("1500"));
    assertThat(server.getRequestCount()).isEqualTo(2);

    AggregationTotals second =
        cache.get(
            key,
            k -> {
              throw new IllegalStateException("缓存命中不得重新计算");
            });
    assertThat(second).isSameAs(first);
    assertThat(computations.get()).isEqualTo(1);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void protectionFailureCachesNothingAndReloadsOnRetry() {
    server.enqueue(json(firstPageBody()));
    server.enqueue(json(firstPageBody()));

    AggregationQueryContext query = context();
    AggregationCache<AggregationTotals> cache =
        new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey key =
        AggregationCacheKey.of("usage-summary", "user-42", query, Map.of());
    AtomicInteger computations = new AtomicInteger();

    for (int attempt = 0; attempt < 2; attempt++) {
      AggregationReadBudget attemptBudget =
          new AggregationReadBudget(1, 200, FIXED.instant().plusSeconds(30), FIXED);
      assertThatThrownBy(
              () ->
                  cache.get(
                      key,
                      k -> {
                        computations.incrementAndGet();
                        List<AggregationLogRecord> records =
                            AggregationLogReader.readSuccessLogs(
                                SESSION, query, null, null, attemptBudget, 2, Duration.ofHours(168), client());
                        AggregationAccumulator accumulator = new AggregationAccumulator();
                        records.forEach(accumulator::add);
                        return accumulator.totals();
                      }))
          .isInstanceOf(PortalException.class)
          .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    }
    assertThat(computations.get()).isEqualTo(2);
  }
}
