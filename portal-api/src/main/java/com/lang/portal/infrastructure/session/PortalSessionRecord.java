package com.lang.portal.infrastructure.session;

/**
 * Portal 服务端会话记录。浏览器持有的是 {@code sessionId}，上游凭证只保存在服务端。
 *
 * @param sessionId 服务端签发的不透明会话标识，浏览器可见
 * @param upstreamSessionValue 上游会话值，仅服务端可见，不进入 Cookie、日志与指标
 * @param userId 会话所属用户
 */
public record PortalSessionRecord(String sessionId, String upstreamSessionValue, long userId) {

  public PortalSessionRecord {
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("会话标识不能为空");
    }
    if (upstreamSessionValue == null || upstreamSessionValue.isBlank()) {
      throw new IllegalArgumentException("上游会话值不能为空");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("用户标识必须为正数");
    }
  }

  /** 不输出任何凭证材料：会话标识与上游会话值都不应出现在日志、异常信息或调试输出里。 */
  @Override
  public String toString() {
    return "PortalSessionRecord[credentials=<redacted>]";
  }
}