package com.lang.portal.base.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.infrastructure.session.PortalSessionRecord;
import com.lang.portal.infrastructure.session.PortalSessionStore;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PortalSessionAuthenticationFilter extends OncePerRequestFilter {

  /** 已校验通过的上游会话，供 Controller 拼上游请求头，避免再次从 Cookie 取值。 */
  public static final String UPSTREAM_SESSION_ATTRIBUTE = "portal.auth.upstream-session";

  private static final Logger log = LoggerFactory.getLogger(PortalSessionAuthenticationFilter.class);

  private final PortalCommonProperties properties;
  private final NewApiAuthenticationClient authenticationClient;
  private final NewApiCookiePolicy cookiePolicy;
  private final PortalSessionStore sessionStore;

  public PortalSessionAuthenticationFilter(
      PortalCommonProperties properties,
      NewApiAuthenticationClient authenticationClient,
      NewApiCookiePolicy cookiePolicy,
      PortalSessionStore sessionStore) {
    this.properties = properties;
    this.authenticationClient = authenticationClient;
    this.cookiePolicy = cookiePolicy;
    this.sessionStore = sessionStore;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/portal/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    BrowserSession browserSession = session(request.getCookies());
    if (browserSession == null) {
      if (hasSessionCookie(request.getCookies())) {
        expireSessionCookies(response);
      }
      chain.doFilter(request, response);
      return;
    }
    PortalSessionRecord record;
    try {
      record = sessionStore.find(browserSession.sessionId());
    } catch (RuntimeException e) {
      // 存储不可用时直接给出稳定的 503：既不放行，也不当成“没有这条会话”——
      // 后者会把一次存储故障变成对全部在线用户的静默登出。
      log.warn("event=session_store_unavailable kind={}", e.getClass().getSimpleName());
      writeStorageUnavailable(request, response);
      return;
    }
    if (record == null || record.userId() != browserSession.userId()) {
      // 服务端没有这条记录（已退出、已过期或 Cookie 为旧格式），或 Cookie 声称的用户与记录不符：
      // 不得访问上游，也不得重建记录。
      log.warn("event=session_invalid");
      expireSessionCookies(response);
      chain.doFilter(request, response);
      return;
    }
    NewApiSession session = new NewApiSession(record.upstreamSessionValue(), record.userId());
    try {
      NewApiUserProfile user = authenticationClient.currentUser(session);
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          new PortalAuthenticatedUser(user.id(), user.username(), user.displayName(), user.email()),
          null,
          AuthorityUtils.createAuthorityList("ROLE_USER"));
      SecurityContextHolder.getContext().setAuthentication(authentication);
      request.setAttribute(UPSTREAM_SESSION_ATTRIBUTE, session);
    } catch (PortalException e) {
      if (e.errorCode() != PortalErrorCode.UNAUTHENTICATED) {
        throw e;
      }
      log.warn("event=session_invalid");
      expireSessionCookies(response);
    }
    chain.doFilter(request, response);
  }

  private BrowserSession session(Cookie[] cookies) {
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
      return id > 0 ? new BrowserSession(session, id) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void expireSessionCookies(HttpServletResponse response) {
    cookiePolicy.expireSessionCookies().forEach(cookie -> response.addHeader("Set-Cookie", cookie.toString()));
  }

  private void writeStorageUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Object requestIdAttr = request.getAttribute(RequestIds.ATTRIBUTE);
    String requestId = requestIdAttr instanceof String s && !s.isBlank() ? s : RequestIds.generate();
    PortalErrorCode code = PortalErrorCode.UPSTREAM_UNAVAILABLE;
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(RequestIds.HEADER, requestId);
    new ObjectMapper().writeValue(response.getOutputStream(),
        ApiResponses.failure(requestId, code.name(), code.message()));
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

  /** 浏览器持有的会话标识与用户标识；本身不构成有效会话，必须经服务端记录确认。 */
  private record BrowserSession(String sessionId, long userId) {}
}
