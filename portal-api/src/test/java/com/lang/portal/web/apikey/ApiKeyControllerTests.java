package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.PageData;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.web.auth.AuthCsrfService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiKeyControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private final ApiKeyApplicationService service = mock(ApiKeyApplicationService.class);
  private final ApiKeyQueryService queryService = mock(ApiKeyQueryService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private ApiKeyController controller() {
    properties.auth().setAllowedOrigins("http://portal.test");
    return new ApiKeyController(service, queryService, new AuthCsrfService(properties), properties);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"),
        new Cookie("LANG_UID", "42"),
        new Cookie("XSRF-TOKEN", "csrf-token"));
    request.addHeader("X-XSRF-TOKEN", "csrf-token");
    request.addHeader("Origin", "http://portal.test");
    return request;
  }

  @Test
  void listAppliesDefaultsAndResolvesSession() {
    when(queryService.list(SESSION, 1, 20, null, null))
        .thenReturn(PageData.of(List.of(), 1, 20, 0));

    var response = controller().list(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().total()).isEqualTo(0);
    verify(queryService, times(1)).list(SESSION, 1, 20, null, null);
  }

  @Test
  void listRejectsUnknownQueryAndBadPageBeforeService() {
    assertThatThrownBy(() -> controller().list(Map.of("foo", "1"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> controller().list(Map.of("page", "abc"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> controller().list(Map.of("pageSize", "9999"), USER, validRequest()))
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

  @Test
  void detailRejectsIllegalId() {
    assertThatThrownBy(() -> controller().detail("abc", USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
    verifyNoInteractions(queryService);
  }

  @Test
  void createDelegatesAfterCsrf() {
    var body = new CreateApiKeyRequest("key-1", false, 1L, null, null, null);
    when(service.create(eq(SESSION), eq(body))).thenReturn(new ApiKeyCreateResponse(true));

    var response = controller().create(body, USER, validRequest());

    assertThat(response.getBody().data().created()).isTrue();
    verify(service, times(1)).create(SESSION, body);
  }

  @Test
  void createWithBadCsrfNeverReachesService() {
    var body = new CreateApiKeyRequest("key-1", false, 1L, null, null, null);
    MockHttpServletRequest request = validRequest();
    request.removeHeader("X-XSRF-TOKEN");

    assertThatThrownBy(() -> controller().create(body, USER, request))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.CSRF_REJECTED);
    verifyNoInteractions(service);
  }

  @Test
  void updateStatusAndDeleteDelegate() {
    when(service.update(eq(SESSION), eq(7L), any())).thenReturn(new ApiKeyUpdateResponse(true));
    controller().update("7", new UpdateApiKeyRequest("n", null, null, null, null, null), USER, validRequest());
    verify(service, times(1)).update(eq(SESSION), eq(7L), any());

    when(service.updateStatus(SESSION, 7L, false)).thenReturn(new ApiKeyStatusResponse(false));
    controller().status("7", new ApiKeyStatusRequest(false), USER, validRequest());
    verify(service, times(1)).updateStatus(SESSION, 7L, false);

    assertThatThrownBy(() -> controller().status("7", new ApiKeyStatusRequest(null), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);

    when(service.delete(SESSION, 7L)).thenReturn(new ApiKeyDeleteResponse(true));
    assertThat(controller().delete("7", USER, validRequest()).getBody().data().deleted()).isTrue();
  }

  @Test
  void revealSetsNoStoreHeaders() {
    when(service.reveal(SESSION, 7L)).thenReturn(new ApiKeyRevealResponse("sk-abc"));

    var response = controller().reveal("7", USER, validRequest());

    assertThat(response.getBody().data().secret()).isEqualTo("sk-abc");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
    assertThat(response.getHeaders().get("ETag")).isNull();
  }

  @Test
  void revealBadIdAndBadCsrf() {
    assertThatThrownBy(() -> controller().reveal("0", USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
    verifyNoInteractions(service);

    MockHttpServletRequest request = validRequest();
    request.removeHeader("X-XSRF-TOKEN");
    assertThatThrownBy(() -> controller().reveal("7", USER, request))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.CSRF_REJECTED);
  }
}
