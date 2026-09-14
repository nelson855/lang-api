package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountTopupControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private final AccountQueryService queryService = mock(AccountQueryService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private AccountController controller() {
    return new AccountController(queryService, properties);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    return request;
  }

  @Test
  void topupOptionsReturnsDisabledWithoutLeaking() {
    when(queryService.topupOptions(eq(SESSION)))
        .thenReturn(new TopupCapability(false, List.of(), "USD", "NOT_CONFIGURED"));

    var response = controller().topupOptions(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().enabled()).isFalse();
    assertThat(response.getBody().data().reason()).isEqualTo("NOT_CONFIGURED");
    assertThat(response.getBody().data().methods()).isEmpty();
  }

  @Test
  void topupOptionsUnsupportedStaysDisabled() {
    when(queryService.topupOptions(eq(SESSION)))
        .thenReturn(new TopupCapability(false, List.of(), "USD", "UNSUPPORTED_PROVIDER"));

    var response = controller().topupOptions(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().enabled()).isFalse();
    assertThat(response.getBody().data().reason()).isEqualTo("UNSUPPORTED_PROVIDER");
    assertThat(response.getBody().data().currency()).isEqualTo("USD");
  }

  @Test
  void topupOptionsUpstreamFailurePropagates() {
    when(queryService.topupOptions(eq(SESSION)))
        .thenThrow(new com.lang.portal.base.exception.UpstreamException(
            com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_ERROR));

    assertThatThrownBy(() -> controller().topupOptions(Map.of(), USER, validRequest()))
        .matches(e -> e instanceof com.lang.portal.base.exception.UpstreamException);
  }

  @Test
  void topupOptionsRejectsQueryParams() {
    assertThatThrownBy(() -> controller().topupOptions(Map.of("verbose", "true"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(queryService);
  }

  @Test
  void topupsReturnsOwnPageOnly() {
    TopupRecord record =
        new TopupRecord("ORD-1", "10", "USD", "ALIPAY", "SUCCEEDED", "2023-11-14T22:13:20Z", "2023-11-14T22:15:00Z");
    when(queryService.topups(eq(SESSION), eq(1), eq(20)))
        .thenReturn(PageData.of(List.of(record), 1, 20, 1L));

    var response = controller().topups(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().items()).hasSize(1);
    assertThat(response.getBody().data().total()).isEqualTo(1L);
  }

  @Test
  void topupsRejectsIllegalOrPrivilegedParams() {
    assertThatThrownBy(() -> controller().topups(Map.of("page", "0"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> controller().topups(Map.of("userId", "1"), USER, validRequest()))
        .isInstanceOf(PortalException.class);
    verifyNoInteractions(queryService);
  }

  @Test
  void anonymousTopupsIsUnauthenticated() {
    assertThatThrownBy(() -> controller().topups(Map.of(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }

  @Test
  void anonymousTopupOptionsIsUnauthenticated() {
    assertThatThrownBy(() -> controller().topupOptions(Map.of(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }

  @Test
  void topupsEmptyPageReturnsEmptyItems() {
    when(queryService.topups(eq(SESSION), eq(1), eq(20)))
        .thenReturn(PageData.of(List.of(), 1, 20, 0L));

    var response = controller().topups(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().items()).isEmpty();
    assertThat(response.getBody().data().total()).isZero();
  }

  @Test
  void topupsUpstreamFailurePropagates() {
    when(queryService.topups(eq(SESSION), eq(1), eq(20)))
        .thenThrow(new com.lang.portal.base.exception.UpstreamException(
            com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_ERROR));

    assertThatThrownBy(() -> controller().topups(Map.of(), USER, validRequest()))
        .matches(e -> e instanceof com.lang.portal.base.exception.UpstreamException);
  }
}
