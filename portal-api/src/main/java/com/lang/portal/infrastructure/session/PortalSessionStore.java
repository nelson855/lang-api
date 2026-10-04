package com.lang.portal.infrastructure.session;

/**
 * Portal 服务端会话存储。
 *
 * <p>实现必须满足：记录跨进程共享（多个 Lang API 实例立即一致）、进程或存储重启后已删除的记录不会
 * 重新出现、按标识查不到即为不存在且不得用其他入参重建。存储故障必须抛出，由调用方决定失败语义，
 * 不得静默当作“没有这条会话”。
 */
public interface PortalSessionStore {

  /**
   * 为一次登录签发新的独立会话。
   *
   * @param upstreamSessionValue 上游登录返回的会话值
   * @param userId 会话所属用户
   * @return 服务端签发的不透明会话标识
   */
  String create(String upstreamSessionValue, long userId);

  /**
   * 按浏览器持有的会话标识查找记录。
   *
   * @return 记录；不存在或已过期时返回 {@code null}
   */
  PortalSessionRecord find(String sessionId);

  /**
   * 删除会话记录，使该会话立即失效。
   *
   * @return 该记录原本存在并已删除时为 {@code true}；原本就不存在时为 {@code false}
   */
  boolean delete(String sessionId);
}