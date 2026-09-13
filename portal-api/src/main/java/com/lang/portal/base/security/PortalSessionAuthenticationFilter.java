package com.lang.portal.base.security;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PortalSessionAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(PortalSessionAuthenticationFilter.class);

  private final PortalCommonProperties properties;
  private final NewApiAuthenticationClient authenticationClient;
  private final NewApiCookiePolicy cookiePolicy;

  public PortalSessionAuthenticationFilter(
      PortalCommonProperties properties,
      NewApiAuthenticationClient authenticationClient,
      NewApiCookiePolicy cookiePolicy) {
    this.properties = properties;
    this.authenticationClient = authenticationClient;
    this.cookiePolicy = cookiePolicy;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/portal/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    NewApiSession session = session(request.getCookies());
    if (session != null) {
      try {
        NewApiUserProfile user = authenticationClient.currentUser(session);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            new PortalAuthenticatedUser(user.id(), user.username(), user.displayName(), user.email()),
            null,
            AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (PortalException e) {
        if (e.errorCode() != PortalErrorCode.UNAUTHENTICATED) {
          throw e;
        }
        log.warn("event=session_invalid");
        expireSessionCookies(response);
      }
    } else if (hasSessionCookie(request.getCookies())) {
      expireSessionCookies(response);
    }
    chain.doFilter(request, response);
  }

  private NewApiSession session(Cookie[] cookies) {
    if (cookies == null) {
      return null;
    }
    Map<String, String> values = new HashMap<>();
    for (Cookie cookie : cookies) {
      if (!properties.auth().cookie().sessionName().equals(cookie.getName())
          && !properties.auth().cookie().userIdName().equals(cookie.getName())) {
        continue;
      }
      if (values.putIfAbsent(cookie.getName(), cookie.getValue()) != null) {
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
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private boolean hasSessionCookie(Cookie[] cookies) {
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      if (properties.auth().cookie().sessionName().equals(cookie.getName())
          || properties.auth().cookie().userIdName().equals(cookie.getName())) {
        return true;
      }
    }
    return false;
  }

  private void expireSessionCookies(HttpServletResponse response) {
    cookiePolicy.expireSessionCookies().forEach(cookie -> response.addHeader("Set-Cookie", cookie.toString()));
  }
}
