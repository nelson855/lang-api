package com.lang.portal.web.auth;

/**
 * 登录后交给浏览器的会话句柄。
 *
 * @param sessionId 服务端签发的不透明会话标识，写入 {@code LANG_SESSION}
 * @param userId 会话所属用户，写入 {@code LANG_UID}
 */
public record PortalSessionHandle(String sessionId, long userId) {

  public PortalSessionHandle {
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("会话标识不能为空");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("用户标识必须为正数");
    }
  }
}