package com.lang.portal.web.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.PageData;
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

class RequestLogControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final RequestLogQueryService queryService = mock(RequestLogQueryService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private RequestLogController controller() {
    return new RequestLogController(queryService, properties, FIXED);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    return request;
  }

  @Test
  void listAppliesDefaultsForResultPaginationAndTime() {
    when(queryService.list(eq(SESSION), eq(1), eq(20), eq("SUCCESS"), any(), any(), any(), any(), any()))
        .thenReturn(PageData.of(List.of(), 1, 20, 0));

    var response = controller().list(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().total()).isZero();
  }

  @Test
  void listRejectsUnknownAndIllegalParamsBeforeService() {
    assertThatThrownBy(() -> controller().list(Map.of("userId", "42"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> controller().list(Map.of("result", "ALL"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> controller().list(Map.of("pageSize", "101"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () -> controller().list(Map.of("startTime", "2026-09-10T10:00:00Z"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .list(
                        Map.of(
                            "startTime", "2026-09-10T11:00:00Z",
                            "endTime", "2026-09-10T10:00:00Z"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .list(
                        Map.of(
                            "startTime", "2026-09-10T10:00:00.123Z",
                            "endTime", "2026-09-10T11:00:00Z"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(
            () ->
                controller()
                    .list(
                        Map.of(
                            "startTime", "2026-08-01T00:00:00Z",
                            "endTime", "2026-09-10T12:00:00Z"),
                        USER,
                        validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(queryService);
  }

  @Test
  void listWithoutSessionIsUnauthenticated() {
    assertThatThrownBy(() -> controller().list(Map.of(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }
}
