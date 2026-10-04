package com.lang.portal.infrastructure.aggregation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class AggregationCache<V> {

  public record LoadResult<V>(V value, CacheOutcome outcome) {
    public LoadResult {
      Objects.requireNonNull(value, "缓存值不能为空");
      Objects.requireNonNull(outcome, "缓存结果不能为空");
    }
  }

  private final Cache<AggregationCacheKey, V> cache;

  public AggregationCache(Duration ttl, long maximumSize) {
    this(ttl, maximumSize, Ticker.systemTicker());
  }

  AggregationCache(Duration ttl, long maximumSize, Ticker ticker) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("缓存 TTL 必须为正数");
    }
    if (maximumSize < 1) {
      throw new IllegalArgumentException("缓存最大条目数必须为正数");
    }
    Objects.requireNonNull(ticker, "时钟不能为空");
    this.cache =
        Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).ticker(ticker).build();
  }

  public static <V> AggregationCache<V> fromAggregationConfig(PortalCommonProperties.Aggregation config) {
    Objects.requireNonNull(config, "聚合配置不能为空");
    return new AggregationCache<>(config.cacheTtl(), config.cacheMaximumSize());
  }

  public V get(AggregationCacheKey key, Function<AggregationCacheKey, V> loader) {
    Objects.requireNonNull(key, "缓存键不能为空");
    Objects.requireNonNull(loader, "加载函数不能为空");
    V value = cache.get(key, loader);
    if (value == null) {
      throw new IllegalStateException("聚合缓存不接受空值");
    }
    return value;
  }

  public void invalidate(AggregationCacheKey key) {
    cache.invalidate(key);
  }

  public LoadResult<V> getWithOutcome(AggregationCacheKey key, Function<AggregationCacheKey, V> loader) {
    Objects.requireNonNull(key, "缓存键不能为空");
    Objects.requireNonNull(loader, "加载函数不能为空");
    V present = cache.getIfPresent(key);
    if (present != null) {
      return new LoadResult<>(present, CacheOutcome.HIT);
    }
    AtomicBoolean loaded = new AtomicBoolean(false);
    V value =
        cache.get(
            key,
            k -> {
              loaded.set(true);
              return loader.apply(k);
            });
    if (value == null) {
      throw new IllegalStateException("聚合缓存不接受空值");
    }
    return new LoadResult<>(value, loaded.get() ? CacheOutcome.MISS : CacheOutcome.COALESCED);
  }

  long estimatedSize() {
    return cache.estimatedSize();
  }

  Optional<Long> evictionMaximum() {
    return cache.policy().eviction().map(policy -> policy.getMaximum());
  }

  void cleanUp() {
    cache.cleanUp();
  }
}
