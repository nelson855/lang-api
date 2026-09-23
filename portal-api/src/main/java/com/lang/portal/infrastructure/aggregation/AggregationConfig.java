package com.lang.portal.infrastructure.aggregation;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AggregationConfig {

  @Bean
  public AggregationMetrics aggregationMetrics(MeterRegistry registry) {
    return new AggregationMetrics(registry);
  }
}
