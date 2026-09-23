package com.lang.portal.web.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DashboardStatsControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final DashboardStatsQueryService queryService = mock(DashboardStatsQueryService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private DashboardStatsController controller() {
    return new DashboardStatsController(queryService, properties, FIXED);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    return request;
  }

  private Map<String, String> validParams() {
    return Map.of(
        "startTime", "2026-09-01T00:00:00Z",
        "endTime", "2026-09-01T02:00:00Z",
        "granularity", "HOUR",
        "timezone", "UTC");
  }

  @Test
  void rejectsUnknownParamsShortcutRangesAndIllegalGranularityBeforeUpstream() {
    assertThatThrownBy(
            () ->
                controller()
                    .stats(Map.of("startTime", "2026-09-01T00:00:00Z"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .stats(
                        Map.of(
                            "startTime", "2026-09-01T00:00:00Z",
                            "endTime", "2026-09-01T02:00:00Z",
                            "granularity", "HOUR",
                            "timezone", "UTC",
                            "userId", "42"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .stats(
                        Map.of(
                            "startTime", "7D",
                            "endTime", "2026-09-01T02:00:00Z",
                            "granularity", "HOUR",
                            "timezone", "UTC"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(queryService);
  }

  @Test
  void mismatchedPrincipalAndDuplicateCookiesDoNotLeakAcrossUsers() {
    PortalAuthenticatedUser other =
        new PortalAuthenticatedUser(77L, "other", "Other", "other@example.test");
    assertThatThrownBy(() -> controller().stats(validParams(), other, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);

    MockHttpServletRequest duplicated = new MockHttpServletRequest();
    duplicated.setCookies(
        new Cookie("LANG_SESSION", "a"),
        new Cookie("LANG_SESSION", "b"),
        new Cookie("LANG_UID", "42"));
    assertThatThrownBy(() -> controller().stats(validParams(), USER, duplicated))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }

  @Test
  void legalResponseEchoesNormalizedRangeAndFreshRequestId() {
    DashboardStatsData data =
        new DashboardStatsData(
            "p2-2026-09-22-a",
            new DashboardRangeDto("2026-09-01T00:00:00Z", "2026-09-01T02:00:00Z", "UTC", "HOUR"),
            new DashboardMetricsDto(
                DashboardCountMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardCountMetric.available(60L, "tokens"),
                DashboardMoneyMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardCountMetric.available(2L, "keys"),
                DashboardRatioMetric.unavailable(DashboardReasonCode.BASELINE_NOT_VERIFIED),
                DashboardAverageMetric.unavailable(DashboardReasonCode.NO_DATA)),
            new DashboardRequestTrendDto(
                DashboardAvailability.UNAVAILABLE,
                DashboardReasonCode.BASELINE_NOT_VERIFIED,
                "requests",
                java.util.List.of()),
            new DashboardSpendTrendDto(
                DashboardAvailability.UNAVAILABLE,
                DashboardReasonCode.BASELINE_NOT_VERIFIED,
                null,
                java.util.List.of()),
            new DashboardRecentRequestsDto(
                DashboardAvailability.PARTIAL,
                DashboardReasonCode.PARTIAL_SOURCE_COVERAGE,
                java.util.List.of()));
    when(queryService.query(
            eq(new NewApiSession("upstream-session", 42L)),
            eq("42"),
            any(),
            any(),
            any(),
            any(),
            eq(FIXED)))
        .thenReturn(data);

    MockHttpServletRequest first = validRequest();
    first.setAttribute("lang.requestId", "req-first");
    MockHttpServletRequest second = validRequest();
    second.setAttribute("lang.requestId", "req-second");

    var firstResponse = controller().stats(validParams(), USER, first);
    var secondResponse = controller().stats(validParams(), USER, second);

    assertThat(firstResponse.getBody().data().range().timezone()).isEqualTo("UTC");
    assertThat(firstResponse.getBody().data().baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(firstResponse.getBody().requestId()).isEqualTo("req-first");
    assertThat(secondResponse.getBody().requestId()).isEqualTo("req-second");
  }
}
