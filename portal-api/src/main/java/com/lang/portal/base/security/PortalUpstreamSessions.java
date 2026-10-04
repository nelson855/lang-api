package com.lang.portal.base.security;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 取得已通过 {@link PortalSessionAuthenticationFilter} 校验的上游会话。
 *
 * <p>Controller 只允许从这里取上游凭证：上游会话值只存在于服务端会话记录中，浏览器持有的
 * {@code LANG_SESSION} 是不透明标识，不能直接当作上游凭证使用。
 */
public final class PortalUpstreamSessions {

  private PortalUpstreamSessions() {}

  /**
   * @return 本次请求已校验通过的上游会话
   * @throws PortalException 未通过会话校验时抛出 {@code UNAUTHENTICATED}
   */
  public static NewApiSession require(HttpServletRequest request) {
    Object session = request.getAttribute(PortalSessionAuthenticationFilter.UPSTREAM_SESSION_ATTRIBUTE);
    if (session instanceof NewApiSession upstreamSession) {
      return upstreamSession;
    }
    throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
  }
}