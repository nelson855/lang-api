package com.lang.portal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AggregationPropertiesTests {

  @Test
  void aggregationDefaultsMatchConservativeBaseline() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    assertThat(aggregation.baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(aggregation.pageSize()).isEqualTo(20);
    assertThat(aggregation.maxPages()).isEqualTo(10);
    assertThat(aggregation.maxRecords()).isEqualTo(200);
    assertThat(aggregation.singleCallTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(aggregation.totalTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(aggregation.maxLiveLogRange()).isEqualTo(Duration.ofHours(168));
    assertThat(aggregation.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(aggregation.cacheMaximumSize()).isEqualTo(1000);
    assertThat(aggregation.fiveMinutesMaxSpan()).isEqualTo(Duration.ofHours(24));
    assertThat(aggregation.hourMaxSpan()).isEqualTo(Duration.ofHours(168));
    assertThat(aggregation.dayMaxSpan()).isEqualTo(Duration.ofHours(720));
  }

  @Test
  void validAggregationPassesStartupValidation() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    PortalPropertiesValidator.validateAggregation(aggregation);
  }

  @Test
  void maxRecordsBelowPageSizeFails() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setMaxRecords(10);
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation.max-records");
  }

  @Test
  void maxRecordsAbovePageCapacityFails() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setMaxRecords(201);
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation.max-records");
  }

  @Test
  void totalTimeoutBelowSingleCallTimeoutFails() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setTotalTimeout(Duration.ofSeconds(3));
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation.total-timeout");
  }

  @Test
  void nonPositiveCacheTtlFails() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setCacheTtl(Duration.ZERO);
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation.cache.ttl");
  }

  @Test
  void unknownBaselineVersionFails() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setBaselineVersion("unknown-version");
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation.baseline-version");
  }

  @Test
  void nonMonotonicGranularitySpansFail() {
    PortalCommonProperties.Aggregation aggregation = new PortalCommonProperties.Aggregation();
    aggregation.setHourMaxSpan(Duration.ofHours(12));
    assertThatThrownBy(() -> PortalPropertiesValidator.validateAggregation(aggregation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lang.aggregation");
  }
}
