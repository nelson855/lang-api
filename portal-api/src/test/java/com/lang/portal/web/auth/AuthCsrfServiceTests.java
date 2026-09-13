package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthCsrfServiceTests {

  @Test
  void acceptsOnlyMatchingCsrfCookieAndHeader() {
    AuthCsrfService service = new AuthCsrfService(new PortalCommonProperties());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("XSRF-TOKEN", "token-value"));
    request.addHeader("X-XSRF-TOKEN", "token-value");

    assertThat(service.isValid(request)).isTrue();
    request.removeHeader("X-XSRF-TOKEN");
    assertThat(service.isValid(request)).isFalse();
  }

  @Test
  void issuesTheConfiguredCsrfCookieRatherThanReusingSessionCookieSettings() {
    PortalCommonProperties properties = new PortalCommonProperties();
    PortalCommonProperties.Csrf csrf = new PortalCommonProperties.Csrf();
    csrf.setCookieName("PORTAL_CSRF");
    csrf.setHeaderName("X-PORTAL-CSRF");
    csrf.setPath("/portal/api/auth");
    csrf.setSameSite("Strict");
    properties.auth().setCsrf(csrf);
    AuthCsrfService service = new AuthCsrfService(properties);

    AuthCsrfService.IssuedCsrf issued = service.issue();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("PORTAL_CSRF", issued.token()));
    request.addHeader("X-PORTAL-CSRF", issued.token());

    assertThat(issued.cookie().toString())
        .contains("PORTAL_CSRF=")
        .contains("Path=/portal/api/auth")
        .contains("SameSite=Strict");
    assertThat(service.isValid(request)).isTrue();
  }

  @Test
  void requiresAnExactConfiguredOriginWhenOriginCheckingIsEnabled() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().setAllowedOrigins("https://portal.example, https://admin.example");
    AuthCsrfService service = new AuthCsrfService(properties);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("XSRF-TOKEN", "token-value"));
    request.addHeader("X-XSRF-TOKEN", "token-value");
    request.addHeader("Origin", "https://portal.example.evil");

    assertThatThrownBy(() -> service.requireValid(request))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.CSRF_REJECTED);

    request.removeHeader("Origin");
    request.addHeader("Origin", "https://admin.example");
    service.requireValid(request);
  }
}
