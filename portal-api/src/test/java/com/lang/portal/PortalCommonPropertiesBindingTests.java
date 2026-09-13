package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PortalCommonPropertiesBindingTests {

  @Autowired
  private PortalCommonProperties properties;

  @Autowired
  private Environment environment;

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

  @Test
  void bindsDeterministicTestAuthenticationDefaults() {
    assertThat(environment.getProperty("lang.auth.registration.enabled")).isEqualTo("true");
    assertThat(environment.getProperty("lang.auth.cookie.secure")).isEqualTo("false");
    assertThat(environment.getProperty("lang.auth.allowed-origins")).isEqualTo("http://portal.test");
    assertThat(environment.getProperty("lang.auth.trusted-proxy.cidrs")).isEqualTo("127.0.0.1/32");
    assertThat(environment.getProperty("lang.auth.rate-limit.login.window")).isEqualTo("10s");
    assertThat(environment.getProperty("lang.auth.rate-limit.register.window")).isEqualTo("10s");
  }

  @Test
  void exposesSharedPortalSessionCookieDefaults() {
    assertThat(environment.getProperty("lang.auth.cookie.session-name")).isEqualTo("LANG_SESSION");
    assertThat(environment.getProperty("lang.auth.cookie.user-id-name")).isEqualTo("LANG_UID");
    assertThat(environment.getProperty("lang.auth.cookie.path")).isEqualTo("/portal");
    assertThat(environment.getProperty("lang.auth.cookie.same-site")).isEqualTo("Lax");
  }

  @Test
  void exposesSharedCsrfCookieDefaults() {
    assertThat(environment.getProperty("lang.auth.csrf.cookie-name")).isEqualTo("XSRF-TOKEN");
    assertThat(environment.getProperty("lang.auth.csrf.header-name")).isEqualTo("X-XSRF-TOKEN");
    assertThat(environment.getProperty("lang.auth.csrf.path")).isEqualTo("/portal");
    assertThat(environment.getProperty("lang.auth.csrf.same-site")).isEqualTo("Lax");
  }

  @Test
  void capsPortalSessionLifetimeAtThirtyDays() {
    assertThat(environment.getProperty("lang.auth.cookie.max-age")).isEqualTo("30d");
  }

  @Test
  void exposesSharedAuthenticationRequestProtections() {
    assertThat(environment.getProperty("lang.auth.origin-check-enabled")).isEqualTo("true");
    assertThat(environment.getProperty("lang.auth.rate-limit.login.username-attempts")).isEqualTo("5");
    assertThat(environment.getProperty("lang.auth.rate-limit.login.client-attempts")).isEqualTo("20");
    assertThat(environment.getProperty("lang.auth.rate-limit.register.client-attempts")).isEqualTo("3");
  }

  @Test
  void usesForwardedForOnlyThroughTheConfiguredTrustedProxyPolicy() {
    assertThat(environment.getProperty("lang.auth.trusted-proxy.forwarded-for-header"))
        .isEqualTo("X-Forwarded-For");
  }
}
