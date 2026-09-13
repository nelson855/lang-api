package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.config.PortalCommonProperties;
import org.junit.jupiter.api.Test;

class AuthOptionsServiceTests {

  @Test
  void exposesRegistrationPolicyAndNeverClaimsUnsupportedVerification() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(true);

    AuthOptions options = new AuthOptionsService(properties).getOptions();

    assertThat(options.registrationEnabled()).isTrue();
    assertThat(options.emailVerificationEnabled()).isFalse();
    assertThat(options.captchaEnabled()).isFalse();
  }
}
