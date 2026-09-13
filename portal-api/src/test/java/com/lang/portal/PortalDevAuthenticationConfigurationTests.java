package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class PortalDevAuthenticationConfigurationTests {

  @Autowired
  private Environment environment;

  @Test
  void enablesLocalRegistrationWithDevelopmentCookieAndProxyDefaults() {
    assertThat(environment.getProperty("lang.auth.registration.enabled")).isEqualTo("true");
    assertThat(environment.getProperty("lang.auth.cookie.secure")).isEqualTo("false");
    assertThat(environment.getProperty("lang.auth.allowed-origins")).isEqualTo("http://localhost:5173");
    assertThat(environment.getProperty("lang.auth.trusted-proxy.cidrs")).isEqualTo("172.16.0.0/12");
  }
}
