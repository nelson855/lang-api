package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class AggregationMetricWhitelistTests {

  private static final Set<String> ALLOWED_TAG_KEYS =
      Set.of("operation", "outcome", "source", "cacheOutcome", "reason");
  private static final Set<String> ALLOWED_METER_NAMES =
      Set.of(
          "portal.aggregation.duration",
          "portal.aggregation.upstream.calls",
          "portal.aggregation.upstream.pages",
          "portal.aggregation.upstream.records",
          "portal.aggregation.cache",
          "portal.aggregation.rejections");

  @Test
  void metricTagsUseOnlyLowCardinalityWhitelist() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordAggregation("usage-summary", AggregationOutcome.SUCCESS, Duration.ofMillis(50));
    metrics.recordUpstream(AggregationSource.ERROR_LOG, AggregationOutcome.FAILURE, 3, 60);
    metrics.recordCache("usage-summary", CacheOutcome.COALESCED);
    metrics.recordProtection("usage-summary", ProtectReason.INCONSISTENT_PAGE);

    for (Meter meter : registry.getMeters()) {
      assertThat(meter.getId().getName())
          .as("指标名必须来自固定集合")
          .isIn(ALLOWED_METER_NAMES);
      for (Tag tag : meter.getId().getTags()) {
        assertThat(tag.getKey()).as("指标标签键必须来自白名单").isIn(ALLOWED_TAG_KEYS);
      }
    }
  }

  @Test
  void sensitiveValuesNeverEnterMetricTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AggregationMetrics metrics = new AggregationMetrics(registry);

    metrics.recordAggregation("usage-summary", AggregationOutcome.FAILURE, Duration.ofMillis(5));
    metrics.recordUpstream(AggregationSource.SUCCESS_LOG, AggregationOutcome.SUCCESS, 1, 20);
    metrics.recordCache("usage-summary", CacheOutcome.MISS);
    metrics.recordProtection("usage-summary", ProtectReason.DEADLINE);

    Set<String> tagValues =
        StreamSupport.stream(registry.getMeters().spliterator(), false)
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(Tag::getValue)
            .collect(Collectors.toSet());
    assertThat(tagValues)
        .doesNotContain(
            "user-42",
            "2026-09-01T00:00:00Z",
            "gpt-test",
            "req-1",
            "sk-secret-key",
            "upstream-session",
            "原始上游错误正文",
            "java.lang.RuntimeException: boom");
  }
}
