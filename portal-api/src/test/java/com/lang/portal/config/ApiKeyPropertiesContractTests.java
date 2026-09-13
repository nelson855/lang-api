package com.lang.portal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyPropertiesContractTests {

  @Test
  void exposesSaneApiKeyDefaults() {
    PortalCommonProperties properties = new PortalCommonProperties();
    assertThat(properties.apiKey().defaultPageSize()).isEqualTo(20);
    assertThat(properties.apiKey().maxPageSize()).isEqualTo(100);
    assertThat(properties.apiKey().revealTtl().toSeconds()).isEqualTo(60);
    assertThat(properties.apiKey().statusAggregationMaxPages()).isPositive();
  }

  @Test
  void rejectsDefaultPageSizeLargerThanMax() {
    PortalCommonProperties.ApiKey apiKey = new PortalCommonProperties.ApiKey();
    apiKey.setDefaultPageSize(50);
    apiKey.setMaxPageSize(20);
    assertThatThrownBy(() -> PortalPropertiesValidator.validateApiKey(apiKey))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsNonPositiveAggregationBudget() {
    PortalCommonProperties.ApiKey apiKey = new PortalCommonProperties.ApiKey();
    apiKey.setStatusAggregationMaxPages(0);
    assertThatThrownBy(() -> PortalPropertiesValidator.validateApiKey(apiKey))
        .isInstanceOf(IllegalStateException.class);
  }
}
