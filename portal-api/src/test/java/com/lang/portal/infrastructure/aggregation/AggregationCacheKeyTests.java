package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AggregationCacheKeyTests {

  private AggregationQueryContext context() {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00+08:00",
        "2026-09-02T00:00:00+08:00",
        "Asia/Shanghai",
        AggregationGranularity.DAY,
        "p2-2026-09-22-a",
        new PortalCommonProperties.Aggregation());
  }

  private AggregationCacheKey key() {
    return AggregationCacheKey.of("usage-summary", "user-42", context(), Map.of("model", "gpt-test"));
  }

  @Test
  void keyCarriesAllIdentityDimensions() {
    AggregationCacheKey cacheKey = key();
    assertThat(cacheKey.operation()).isEqualTo("usage-summary");
    assertThat(cacheKey.userId()).isEqualTo("user-42");
    assertThat(cacheKey.start()).isEqualTo(context().start());
    assertThat(cacheKey.end()).isEqualTo(context().end());
    assertThat(cacheKey.granularity()).isEqualTo(AggregationGranularity.DAY);
    assertThat(cacheKey.zoneId()).isEqualTo("Asia/Shanghai");
    assertThat(cacheKey.baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(cacheKey.filters()).isEqualTo(Map.of("model", "gpt-test"));
  }

  @Test
  void sameSemanticsWithReorderedFiltersShareKey() {
    Map<String, String> first = new TreeMap<>(Map.of("b", "2", "a", "1"));
    Map<String, String> second = new TreeMap<>(Map.of("a", "1", "b", "2"));
    assertThat(AggregationCacheKey.of("op", "u", context(), first))
        .isEqualTo(AggregationCacheKey.of("op", "u", context(), second));
  }

  @Test
  void eachIdentityDimensionSeparatesKeys() {
    AggregationCacheKey base = key();
    assertThat(AggregationCacheKey.of("other-op", "user-42", context(), Map.of("model", "gpt-test")))
        .isNotEqualTo(base);
    assertThat(AggregationCacheKey.of("usage-summary", "user-7", context(), Map.of("model", "gpt-test")))
        .isNotEqualTo(base);
    AggregationQueryContext otherRange =
        AggregationQueryContext.of(
            "2026-09-02T00:00:00+08:00",
            "2026-09-03T00:00:00+08:00",
            "Asia/Shanghai",
            AggregationGranularity.DAY,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());
    assertThat(AggregationCacheKey.of("usage-summary", "user-42", otherRange, Map.of("model", "gpt-test")))
        .isNotEqualTo(base);
    assertThat(
            AggregationCacheKey.of(
                "usage-summary", "user-42", context(), Map.of("model", "other-model")))
        .isNotEqualTo(base);
  }

  @Test
  void granularityTimezoneAndBaselineSeparateKeys() {
    AggregationCacheKey base = key();
    AggregationQueryContext hourContext =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00Z",
            "2026-09-01T02:00:00Z",
            "UTC",
            AggregationGranularity.HOUR,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());
    assertThat(AggregationCacheKey.of("usage-summary", "user-42", hourContext, Map.of("model", "gpt-test")))
        .isNotEqualTo(base);
  }

  @Test
  void keyShapeContainsNoSensitiveComponents() {
    var componentNames =
        Arrays.stream(AggregationCacheKey.class.getRecordComponents())
            .map(c -> c.getName())
            .collect(Collectors.toSet());
    assertThat(componentNames)
        .containsExactlyInAnyOrder(
            "operation", "userId", "start", "end", "granularity", "zoneId", "baselineVersion", "filters");
  }

  @Test
  void blankOperationOrUserIsRejected() {
    assertThatThrownBy(() -> AggregationCacheKey.of(" ", "user-42", context(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AggregationCacheKey.of("usage-summary", null, context(), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
