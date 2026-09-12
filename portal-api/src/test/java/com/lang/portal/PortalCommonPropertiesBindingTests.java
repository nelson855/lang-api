package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PortalCommonPropertiesBindingTests {

  @Autowired
  private PortalCommonProperties properties;

  @Test
  void bindsSharedRequestAndBodyLimits() {
    assertThat(properties.request().maxBodyBytes()).isEqualTo(1_048_576);
    assertThat(properties.request().requestId().minLength()).isEqualTo(8);
    assertThat(properties.request().requestId().maxLength()).isEqualTo(64);
  }

  @Test
  void bindsUpstreamPoolAndTimeouts() {
    assertThat(properties.upstream().newApi().baseUrl()).isNotBlank();
    assertThat(properties.upstream().newApi().pool().maxConnections()).isEqualTo(5);
    assertThat(properties.upstream().newApi().pool().maxPendingAcquire()).isEqualTo(5);
    assertThat(properties.upstream().newApi().connectTimeout().toMillis()).isEqualTo(500);
  }

  @Test
  void bindsSiteAndPublicProtocols() {
    assertThat(properties.portal().siteName()).isNotBlank();
    assertThat(properties.portal().publicProtocols()).isNotNull();
  }
}
