package com.lang.portal.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuthenticationPropertiesValidatorTests {

  @Test
  void rejectsInsecureAuthenticationCookieInProduction() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "false")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.cookie.secure：生产环境必须启用 Secure");
  }

  @Test
  void rejectsIllegalSessionCookieName() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.cookie.session-name", "LANG SESSION")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.cookie.session-name：必须是合法 Cookie 名");
  }

  @Test
  void rejectsIllegalUserIdCookieName() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.cookie.user-id-name", "LANG UID")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.cookie.user-id-name：必须是合法 Cookie 名");
  }

  @Test
  void rejectsLoginUsernameThresholdAboveTwentyAttempts() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.rate-limit.login.username-attempts", "21")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.rate-limit.login.username-attempts：不得超过 20");
  }

  @Test
  void rejectsNonPositiveLoginRateLimitWindow() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.rate-limit.login.window", "0s")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.rate-limit.login.window：必须为正数");
  }

  @Test
  void rejectsProfileUpdateUserThresholdAboveTwentyAttempts() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.rate-limit.profile-update.user-attempts", "21")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.rate-limit.profile-update.user-attempts：不得超过 20");
  }

  @Test
  void rejectsNonPositiveProfileUpdateRateLimitWindow() {
    PortalCommonProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "lang.auth.allowed-origins", "https://portal.example",
            "lang.auth.trusted-proxy-cidrs", "10.0.0.0/8",
            "lang.auth.cookie.secure", "true",
            "lang.auth.rate-limit.profile-update.window", "0s")))
        .bind("lang", PortalCommonProperties.class)
        .orElseThrow(() -> new AssertionError("认证配置应可绑定"));

    assertThatThrownBy(() -> PortalPropertiesValidator.validateAuthentication("prod", properties.auth()))
        .hasMessage("非法配置 lang.auth.rate-limit.profile-update.window：必须为正数");
  }
}
