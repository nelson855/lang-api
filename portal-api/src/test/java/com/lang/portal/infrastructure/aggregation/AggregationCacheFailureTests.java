package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AggregationCacheFailureTests {

  private AggregationCacheKey key() {
    AggregationQueryContext context =
        AggregationQueryContext.of(
            "2026-09-01T00:00:00Z",
            "2026-09-01T02:00:00Z",
            "UTC",
            AggregationGranularity.HOUR,
            "p2-2026-09-22-a",
            new PortalCommonProperties.Aggregation());
    return AggregationCacheKey.of("usage-summary", "user-42", context, Map.of());
  }

  @Test
  void upstreamTimeoutIsNotCachedAndRetryReloads() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey cacheKey = key();
    AtomicInteger loads = new AtomicInteger();

    assertThatThrownBy(
            () ->
                cache.get(
                    cacheKey,
                    k -> {
                      loads.incrementAndGet();
                      throw new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
                    }))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT);

    String recovered =
        cache.get(
            cacheKey,
            k -> {
              loads.incrementAndGet();
              return "recovered";
            });
    assertThat(recovered).isEqualTo("recovered");
    assertThat(loads.get()).isEqualTo(2);
  }

  @Test
  void protectionRejectionIsNotCachedAndRetryReloads() {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey cacheKey = key();
    AtomicInteger loads = new AtomicInteger();

    assertThatThrownBy(
            () ->
                cache.get(
                    cacheKey,
                    k -> {
                      loads.incrementAndGet();
                      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "缩小范围后重试");
                    }))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);

    String recovered =
        cache.get(
            cacheKey,
            k -> {
              loads.incrementAndGet();
              return "recovered";
            });
    assertThat(recovered).isEqualTo("recovered");
    assertThat(loads.get()).isEqualTo(2);
  }

  @Test
  void cancelledLoadIsNotCachedAndRetryReloads() throws Exception {
    AggregationCache<String> cache = new AggregationCache<>(Duration.ofSeconds(30), 1000);
    AggregationCacheKey cacheKey = key();
    CountDownLatch loaderEntered = new CountDownLatch(1);
    CountDownLatch releaseLoader = new CountDownLatch(1);
    AtomicInteger loads = new AtomicInteger();
    AtomicReference<Throwable> loadError = new AtomicReference<>();

    Thread loader =
        new Thread(
            () -> {
              try {
                cache.get(
                    cacheKey,
                    k -> {
                      loads.incrementAndGet();
                      loaderEntered.countDown();
                      try {
                        if (!releaseLoader.await(10, TimeUnit.SECONDS)) {
                          throw new IllegalStateException("加载器等待超时");
                        }
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
                      }
                      return "unused";
                    });
              } catch (Throwable e) {
                loadError.set(e);
              }
            });
    loader.start();
    assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
    loader.interrupt();
    loader.join(5000);

    assertThat(loadError.get())
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT);
    String recovered =
        cache.get(
            cacheKey,
            k -> {
              loads.incrementAndGet();
              return "recovered";
            });
    assertThat(recovered).isEqualTo("recovered");
    assertThat(loads.get()).isEqualTo(2);
  }
}
