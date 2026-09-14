package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UsagePropertiesBindingTests {

  @Autowired
  private PortalCommonProperties properties;

  @Autowired
  private Environment environment;

  @Test
  void bindsUsageDefaults() {
    assertThat(properties.portal().usage().defaultRange().toHours()).isEqualTo(24);
    assertThat(properties.portal().usage().maxRange().toDays()).isEqualTo(30);
    assertThat(properties.portal().usage().defaultPageSize()).isEqualTo(20);
    assertThat(properties.portal().usage().maxPageSize()).isEqualTo(100);
  }

  @Test
  void keepsUsageKeysExplicitInTestProfile() {
    assertThat(environment.getProperty("lang.portal.usage.default-range")).isNotBlank();
    assertThat(environment.getProperty("lang.portal.usage.max-range")).isNotBlank();
    assertThat(environment.getProperty("lang.portal.usage.default-page-size")).isEqualTo("20");
    assertThat(environment.getProperty("lang.portal.usage.max-page-size")).isEqualTo("100");
  }
}
