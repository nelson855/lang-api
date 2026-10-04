package com.lang.portal.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingEntry;
import com.lang.portal.upstream.newapi.pricing.NewApiPricingVendor;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogUpstreamFailureTests {

  private CatalogService serviceWith(
      AtomicInteger loads, PricingSnapshotProvider provider) {
    PortalCommonProperties props = new PortalCommonProperties();
    props.catalog().setQuotaPerUsd(500_000L);
    props.catalog().setCacheTtl(Duration.ofSeconds(60));
    PricingSnapshotProvider counting =
        () -> {
          loads.incrementAndGet();
          return provider.fetchSnapshot();
        };
    return new CatalogService(
        counting,
        props,
        Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build());
  }

  private PricingSnapshotProvider.Snapshot validSnapshot() {
    return new PricingSnapshotProvider.Snapshot(
        List.of(
            new NewApiPricingEntry("m1", null, 1, null, new BigDecimal("0.001"), null)),
        List.of(),
        "v1");
  }

  @Test
  void upstreamErrorsPropagateWithoutCaching() {
    for (PortalErrorCode code :
        new PortalErrorCode[] {
          PortalErrorCode.UPSTREAM_ERROR,
          PortalErrorCode.UPSTREAM_UNAVAILABLE,
          PortalErrorCode.UPSTREAM_TIMEOUT
        }) {
      AtomicInteger loads = new AtomicInteger();
      CatalogService service =
          serviceWith(
              loads,
              () -> {
                throw new UpstreamException(code);
              });
      assertThatThrownBy(service::current)
          .isInstanceOf(UpstreamException.class)
          .matches(e -> ((UpstreamException) e).errorCode() == code);
      assertThatThrownBy(service::providers).isInstanceOf(UpstreamException.class);
      assertThat(loads.get()).isEqualTo(2);
    }
  }

  @Test
  void duplicateIdsFailWithoutPartialSnapshot() {
    AtomicInteger loads = new AtomicInteger();
    PricingSnapshotProvider.Snapshot dup =
        new PricingSnapshotProvider.Snapshot(
            List.of(
                new NewApiPricingEntry("dup", null, 1, null, new BigDecimal("0.001"), null),
                new NewApiPricingEntry("dup", null, 1, null, new BigDecimal("0.001"), null)),
            List.of(),
            "v1");
    CatalogService service = serviceWith(loads, () -> dup);
    assertThatThrownBy(service::current).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(service::current).isInstanceOf(IllegalArgumentException.class);
    assertThat(loads.get()).isEqualTo(2);
  }

  @Test
  void unknownVendorKeepsModelWithNullProvider() {
    AtomicInteger loads = new AtomicInteger();
    CatalogService service = serviceWith(loads, this::validSnapshotWithUnknownVendor);
    CatalogData data = service.current();
    assertThat(data.models().get(0).provider()).isNull();
    assertThat(service.findDetails("m1")).isPresent();
    assertThat(service.providers()).isEmpty();
  }

  private PricingSnapshotProvider.Snapshot validSnapshotWithUnknownVendor() {
    return new PricingSnapshotProvider.Snapshot(
        List.of(
            new NewApiPricingEntry("m1", 999, 1, null, new BigDecimal("0.001"), null)),
        List.of(new NewApiPricingVendor(1, "Example")),
        "v1");
  }
}
