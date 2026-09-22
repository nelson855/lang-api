package com.lang.portal.infrastructure.aggregation;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;

public final class AggregationMetrics {

  private final MeterRegistry registry;

  public AggregationMetrics(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "指标注册表不能为空");
  }

  public void recordAggregation(String operation, AggregationOutcome outcome, Duration duration) {
    Objects.requireNonNull(operation, "操作命名空间不能为空");
    Objects.requireNonNull(outcome, "结果不能为空");
    Objects.requireNonNull(duration, "耗时不能为空");
    registry
        .timer("portal.aggregation.duration", "operation", operation, "outcome", outcome.tag())
        .record(duration);
  }

  public void recordUpstream(
      AggregationSource source, AggregationOutcome outcome, int pages, int records) {
    Objects.requireNonNull(source, "上游来源不能为空");
    Objects.requireNonNull(outcome, "结果不能为空");
    registry.counter("portal.aggregation.upstream.calls", "source", source.tag(), "outcome", outcome.tag()).increment();
    registry.counter("portal.aggregation.upstream.pages", "source", source.tag(), "outcome", outcome.tag()).increment(pages);
    registry.counter("portal.aggregation.upstream.records", "source", source.tag(), "outcome", outcome.tag()).increment(records);
  }

  public void recordCache(String operation, CacheOutcome outcome) {
    Objects.requireNonNull(operation, "操作命名空间不能为空");
    Objects.requireNonNull(outcome, "缓存结果不能为空");
    registry.counter("portal.aggregation.cache", "operation", operation, "cacheOutcome", outcome.tag()).increment();
  }

  public void recordProtection(String operation, ProtectReason reason) {
    Objects.requireNonNull(operation, "操作命名空间不能为空");
    Objects.requireNonNull(reason, "拒绝原因不能为空");
    registry.counter("portal.aggregation.rejections", "operation", operation, "reason", reason.tag()).increment();
  }
}
