package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.base.security.PortalSessionAuthenticationFilter;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthenticationSecurityEventTests {

  @Test
  void rateLimitEventsDoNotContainSecretsOrIdentifiers() {
    ListAppender<ILoggingEvent> limiterEvents = attach(AuthenticationRateLimiter.class);
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().trustedProxy().setCidrs("127.0.0.1/32");
    properties.auth().rateLimit().login().setUsernameAttempts(1);
    AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(properties, new PortalClientAddressResolver(properties));
    org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");
    limiter.checkLogin("ordinary@example.test", request);
    assertThatThrownBy(() -> limiter.checkLogin("ordinary@example.test", request))
        .isInstanceOf(PortalException.class);

    assertSafe(limiterEvents, "rate_limited");
  }

  @Test
  void invalidSessionEventsDoNotContainCookieOrClientAddress() throws Exception {
    PortalCommonProperties properties = new PortalCommonProperties();
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    when(client.currentUser(new NewApiSession("upstream-session", 42L)))
        .thenThrow(new PortalException(PortalErrorCode.UNAUTHENTICATED));
    ListAppender<ILoggingEvent> events = attach(PortalSessionAuthenticationFilter.class);
    PortalSessionAuthenticationFilter filter = new PortalSessionAuthenticationFilter(
        properties, client, new NewApiCookiePolicy(properties));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/portal/api/profile");
    request.setRemoteAddr("203.0.113.9");
    request.setCookies(new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));

    filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

    assertSafe(events, "session_invalid");
  }

  @Test
  void UnconfirmedRevocationEventsDoNotContainSessionOrCsrfValues() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("http://portal.test");
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    doThrow(new UpstreamException(PortalErrorCode.UPSTREAM_UNAVAILABLE))
        .when(client).logout(new NewApiSession("upstream-session", 42L));
    AuthenticationController controller = new AuthenticationController(
        new AuthApplicationService(properties, client), new AuthCsrfService(properties),
        new NewApiCookiePolicy(properties), properties,
        new AuthenticationRateLimiter(properties, new PortalClientAddressResolver(properties)));
    ListAppender<ILoggingEvent> events = attach(AuthenticationController.class);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Origin", "http://portal.test");
    request.addHeader("X-XSRF-TOKEN", "csrf-token");
    request.setCookies(new Cookie("XSRF-TOKEN", "csrf-token"),
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));

    assertThatThrownBy(() -> controller.logout(request)).isInstanceOf(UpstreamException.class);

    assertSafe(events, "revocation_unconfirmed");
  }

  private ListAppender<ILoggingEvent> attach(Class<?> type) {
    Logger logger = (Logger) LoggerFactory.getLogger(type);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private void assertSafe(ListAppender<ILoggingEvent> events, String expectedEvent) {
    String output = events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + right);
    assertThat(output).contains("event=" + expectedEvent);
    assertThat(output).doesNotContain(
        "ordinary", "correct-horse", "203.0.113.9", "upstream-session", "csrf-token");
  }
}
