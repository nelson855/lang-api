package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthenticationRateLimiterTests {

  @Test
  void usesForwardedClientAddressOnlyWhenImmediatePeerIsTrusted() {
    PortalCommonProperties properties = properties();
    PortalClientAddressResolver resolver = new PortalClientAddressResolver(properties);

    assertThat(resolver.resolve(request("127.0.0.1", "203.0.113.9, 10.0.0.1"))).isEqualTo("203.0.113.9");
    assertThat(resolver.resolve(request("198.51.100.4", "203.0.113.9"))).isEqualTo("198.51.100.4");
  }

  @Test
  void appliesUsernameAndClientLoginLimitsAndRegistrationLimitBeforeUpstream() {
    PortalCommonProperties properties = properties();
    properties.auth().rateLimit().login().setUsernameAttempts(2);
    properties.auth().rateLimit().login().setClientAttempts(3);
    properties.auth().rateLimit().registration().setClientAttempts(1);
    MutableClock clock = new MutableClock();
    AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(properties, new PortalClientAddressResolver(properties), clock);
    MockHttpServletRequest request = request("127.0.0.1", "203.0.113.9");

    limiter.checkLogin("ordinary", request);
    limiter.checkLogin("ordinary", request);
    assertRateLimited(() -> limiter.checkLogin("ordinary", request));
    limiter.checkLogin("another-user", request);
    assertRateLimited(() -> limiter.checkLogin("third-user", request));

    limiter.checkRegistration(request);
    assertRateLimited(() -> limiter.checkRegistration(request));
  }

  @Test
  void expiresWindowsAndEvictsOldestKeysAtConfiguredCapacity() {
    PortalCommonProperties properties = properties();
    properties.auth().rateLimit().login().setUsernameAttempts(1);
    properties.auth().rateLimit().login().setClientAttempts(100);
    properties.auth().rateLimit().setMaxEntries(2);
    MutableClock clock = new MutableClock();
    AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(properties, new PortalClientAddressResolver(properties), clock);
    MockHttpServletRequest request = request("127.0.0.1", "203.0.113.9");

    limiter.checkLogin("first", request);
    limiter.checkLogin("second", request);
    limiter.checkLogin("third", request);
    assertThat(limiter.trackedKeyCount()).isLessThanOrEqualTo(2);

    clock.advanceSeconds(11);
    limiter.checkLogin("third", request);
  }

  private PortalCommonProperties properties() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().trustedProxy().setCidrs("127.0.0.1/32");
    properties.auth().trustedProxy().setForwardedForHeader("X-Forwarded-For");
    properties.auth().rateLimit().login().setWindow(java.time.Duration.ofSeconds(10));
    properties.auth().rateLimit().registration().setWindow(java.time.Duration.ofSeconds(10));
    return properties;
  }

  private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddress);
    request.addHeader("X-Forwarded-For", forwardedFor);
    return request;
  }

  private void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.RATE_LIMITED);
  }

  private static final class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-09-13T00:00:00Z");

    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }
}
