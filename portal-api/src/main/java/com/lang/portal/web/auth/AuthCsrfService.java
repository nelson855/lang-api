package com.lang.portal.web.auth;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCsrfService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final PortalCommonProperties properties;

  public AuthCsrfService(PortalCommonProperties properties) {
    this.properties = properties;
  }

  public boolean isValid(HttpServletRequest request) {
    String cookie = cookieValue(request, properties.auth().csrf().cookieName());
    String header = request.getHeader(properties.auth().csrf().headerName());
    if (cookie == null || header == null) {
      return false;
    }
    return MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
  }

  public void requireValid(HttpServletRequest request) {
    if (!isValid(request) || !isAllowedOrigin(request)) {
      throw new PortalException(PortalErrorCode.CSRF_REJECTED);
    }
  }

  public IssuedCsrf issue() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    ResponseCookie cookie = ResponseCookie.from(properties.auth().csrf().cookieName(), token)
        .path(properties.auth().csrf().path())
        .sameSite(properties.auth().csrf().sameSite())
        .secure(properties.auth().cookie().secure())
        .httpOnly(false)
        .build();
    return new IssuedCsrf(token, cookie);
  }

  private boolean isAllowedOrigin(HttpServletRequest request) {
    if (!properties.auth().originCheckEnabled()) {
      return true;
    }
    String origin = request.getHeader("Origin");
    if (origin == null || origin.isBlank()) {
      return false;
    }
    return java.util.Arrays.stream(properties.auth().allowedOrigins().split(","))
        .map(String::trim)
        .anyMatch(origin::equals);
  }

  private String cookieValue(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  public record IssuedCsrf(String token, ResponseCookie cookie) {}
}
