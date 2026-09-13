package com.lang.portal.web.auth;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AuthenticationRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationRateLimiter.class);

  private final PortalCommonProperties properties;
  private final PortalClientAddressResolver addressResolver;
  private final Clock clock;
  private final byte[] salt = new byte[32];
  private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);

  @Autowired
  public AuthenticationRateLimiter(PortalCommonProperties properties, PortalClientAddressResolver addressResolver) {
    this(properties, addressResolver, Clock.systemUTC());
  }

  AuthenticationRateLimiter(
      PortalCommonProperties properties, PortalClientAddressResolver addressResolver, Clock clock) {
    this.properties = properties;
    this.addressResolver = addressResolver;
    this.clock = clock;
    new SecureRandom().nextBytes(salt);
  }

  public void checkLogin(String username, HttpServletRequest request) {
    PortalCommonProperties.LoginRateLimit login = properties.auth().rateLimit().login();
    check("login:username:" + digest(username), login.usernameAttempts(), login.window());
    check("login:client:" + digest(addressResolver.resolve(request)), login.clientAttempts(), login.window());
  }

  public void checkRegistration(HttpServletRequest request) {
    PortalCommonProperties.RegistrationRateLimit registration = properties.auth().rateLimit().registration();
    check("registration:client:" + digest(addressResolver.resolve(request)), registration.clientAttempts(), registration.window());
  }

  synchronized int trackedKeyCount() {
    return windows.size();
  }

  private synchronized void check(String key, int limit, Duration window) {
    if (limit < 1 || window.isZero() || window.isNegative()) {
      throw new IllegalStateException("认证限流配置必须为正数");
    }
    Instant now = clock.instant();
    Window current = windows.get(key);
    if (current == null || !now.isBefore(current.startedAt().plus(window))) {
      current = new Window(now, 0);
    }
    if (current.count() >= limit) {
      log.warn("event=rate_limited");
      throw new PortalException(PortalErrorCode.RATE_LIMITED);
    }
    windows.put(key, new Window(current.startedAt(), current.count() + 1));
    while (windows.size() > properties.auth().rateLimit().maxEntries()) {
      windows.remove(windows.keySet().iterator().next());
    }
  }

  private String digest(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(salt);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(
          digest.digest(value.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成认证限流键", exception);
    }
  }

  private record Window(Instant startedAt, int count) {}
}
