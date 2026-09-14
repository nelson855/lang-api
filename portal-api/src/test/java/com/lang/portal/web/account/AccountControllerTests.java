package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.balance.NewApiBalanceClient;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountControllerTests {

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
  void balanceReturnsQuotaAndUsdWithNoStore() {
    when(queryService.balance(eq(SESSION))).thenReturn(new AccountBalanceDto("500000", "1.0", "USD"));

    var response = controller().balance(Map.of(), USER, validRequest());

    assertThat(response.getBody().data().amount()).isEqualTo("1.0");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
  }

  @Test
  void balanceRejectsQueryParams() {
    assertThatThrownBy(() -> controller().balance(Map.of("verbose", "true"), USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(queryService);
  }

  @Test
  void anonymousBalanceIsUnauthenticated() {
    assertThatThrownBy(() -> controller().balance(Map.of(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(queryService);
  }
}
