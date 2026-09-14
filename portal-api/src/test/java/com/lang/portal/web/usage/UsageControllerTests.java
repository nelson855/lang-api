package com.lang.portal.web.usage;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UsageControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final UsageQueryService queryService = mock(UsageQueryService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private UsageController controller() {
    return new UsageController(queryService, properties, FIXED);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    return request;
  }

  @Test
  void summaryReturnsConsumptionWithRateWindow() {
    when(queryService.summary(eq(SESSION), any(), any(), eq(FIXED)))
        .thenReturn(new UsageSummaryDto("1200", "0.0024", "USD", 30L, 4000L, 60));

    var response = controller().summary(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().rateWindowSeconds()).isEqualTo(60);
    assertThat(response.getBody().data().quota()).isEqualTo("1200");
  }

  @Test
  void timeseriesReturnsHourlyPoints() {
    when(queryService.timeseries(eq(SESSION), any(), any(), eq(FIXED)))
        .thenReturn(
            new UsageTimeseriesDto(
                "HOUR",
                List.of(new UsageTimeseriesPoint("2026-09-10T09:00:00Z", 8L, 150L, "700", "0.0014"))));

    var response = controller().timeseries(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().granularity()).isEqualTo("HOUR");
    assertThat(response.getBody().data().points()).hasSize(1);
  }

  @Test
  void rejectsUnknownParamsAndIllegalRangeBeforeService() {
    assertThatThrownBy(() -> controller().summary(Map.of("userId", "42"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .timeseries(
                        Map.of(
                            "startTime", "2026-09-10T11:00:00Z",
                            "endTime", "2026-09-10T10:00:00Z"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(queryService);
  }

  @Test
  void anonymousSummaryIsUnauthenticated() {
    assertThatThrownBy(() -> controller().summary(Map.of(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }
}
