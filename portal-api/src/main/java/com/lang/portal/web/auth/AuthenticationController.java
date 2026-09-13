package com.lang.portal.web.auth;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/portal/api/auth")
public class AuthenticationController {

  public static final String EXPIRE_SESSION_COOKIES_ATTRIBUTE = "portal.auth.expire-session-cookies";
  private static final Logger log = LoggerFactory.getLogger(AuthenticationController.class);

  private final AuthApplicationService service;
  private final AuthCsrfService csrfService;
  private final NewApiCookiePolicy cookiePolicy;
  private final PortalCommonProperties properties;
  private final AuthenticationRateLimiter rateLimiter;

  public AuthenticationController(
      AuthApplicationService service,
      AuthCsrfService csrfService,
      NewApiCookiePolicy cookiePolicy,
      PortalCommonProperties properties,
      AuthenticationRateLimiter rateLimiter) {
    this.service = service;
    this.csrfService = csrfService;
    this.cookiePolicy = cookiePolicy;
    this.properties = properties;
    this.rateLimiter = rateLimiter;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<Void>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    csrfService.requireValid(servletRequest);
    rateLimiter.checkRegistration(servletRequest);
    service.register(request);
    return ResponseEntity.ok(ApiResponses.ok(servletRequest, null));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthProfile>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    csrfService.requireValid(servletRequest);
    rateLimiter.checkLogin(request.username(), servletRequest);
    AuthLoginResult result = service.login(request);
    ResponseEntity.BodyBuilder response = ResponseEntity.ok();
    cookiePolicy.createSessionCookies(result.session().value(), result.session().userId())
        .forEach(cookie -> response.header("Set-Cookie", cookie.toString()));
    return response.body(ApiResponses.ok(servletRequest, result.profile()));
  }

  public ResponseEntity<ApiResponse<AuthProfile>> profile(
      @AuthenticationPrincipal PortalAuthenticatedUser user, HttpServletRequest servletRequest) {
    return ResponseEntity.ok(ApiResponses.ok(servletRequest, profile(user)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthProfile>> refresh(
      @AuthenticationPrincipal PortalAuthenticatedUser user, HttpServletRequest servletRequest) {
    csrfService.requireValid(servletRequest);
    return ResponseEntity.ok(ApiResponses.ok(servletRequest, profile(user)));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
    csrfService.requireValid(servletRequest);
    List<String> expiredCookies = cookiePolicy.expireSessionCookies().stream()
        .map(Object::toString)
        .toList();
    NewApiSession session = session(servletRequest.getCookies());
    if (session != null) {
      try {
        service.logout(session);
      } catch (com.lang.portal.base.exception.UpstreamException exception) {
        servletRequest.setAttribute(EXPIRE_SESSION_COOKIES_ATTRIBUTE, expiredCookies);
        log.warn("event=revocation_unconfirmed");
        throw exception;
      }
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.ok();
    expiredCookies.forEach(cookie -> response.header("Set-Cookie", cookie));
    return response.body(ApiResponses.ok(servletRequest, null));
  }

  private AuthProfile profile(PortalAuthenticatedUser user) {
    return new AuthProfile(user.id(), user.username(), user.displayName(), user.email());
  }

  private NewApiSession session(Cookie[] cookies) {
    if (cookies == null) {
      return null;
    }
    Map<String, String> values = new HashMap<>();
    for (Cookie cookie : cookies) {
      String name = cookie.getName();
      if (!cookiePolicyName(name)) {
        continue;
      }
      if (values.putIfAbsent(name, cookie.getValue()) != null) {
        return null;
      }
    }
    String session = values.get(properties.auth().cookie().sessionName());
    String userId = values.get(properties.auth().cookie().userIdName());
    if (session == null || session.isBlank() || userId == null || userId.isBlank()) {
      return null;
    }
    try {
      long id = Long.parseLong(userId);
      return id > 0 ? new NewApiSession(session, id) : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private boolean cookiePolicyName(String name) {
    return properties.auth().cookie().sessionName().equals(name)
        || properties.auth().cookie().userIdName().equals(name);
  }
}
