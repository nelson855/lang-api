package com.lang.portal.infrastructure.session;

import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis 的 Portal 服务端会话存储，是生产环境的权威实现。
 *
 * <p>记录由所有 Lang API 实例共享，因此撤销对其他实例立即生效；删除是真正的删除，重启后不会因为
 * 旧 Cookie 重新出现。Redis 不可用时由 {@link StringRedisTemplate} 抛出异常向上传播，调用方按
 * “认证失败”处理，不放行任何请求。
 */
public class RedisPortalSessionStore implements PortalSessionStore {

  private static final Logger log = LoggerFactory.getLogger(RedisPortalSessionStore.class);
  private static final String KEY_PREFIX = "lang:portal:session:";
  private static final String FIELD_UPSTREAM_SESSION = "upstream";
  private static final String FIELD_USER_ID = "uid";
  private static final int SESSION_ID_BYTES = 32;

  private final StringRedisTemplate redis;
  private final Duration ttl;
  private final java.security.SecureRandom random = new java.security.SecureRandom();

  public RedisPortalSessionStore(StringRedisTemplate redis, Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  @Override
  public String create(String upstreamSessionValue, long userId) {
    String sessionId = newIdentifier();
    String key = KEY_PREFIX + sessionId;
    redis.opsForHash()
        .put(key, FIELD_UPSTREAM_SESSION, upstreamSessionValue);
    redis.opsForHash().put(key, FIELD_USER_ID, Long.toString(userId));
    // TTL 由 Redis 负责过期清理，应用重启不改变记录的到期时间。
    redis.expire(key, ttl);
    return sessionId;
  }

  @Override
  public PortalSessionRecord find(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    var entries = redis.opsForHash().entries(KEY_PREFIX + sessionId);
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    Object upstream = entries.get(FIELD_UPSTREAM_SESSION);
    Object userId = entries.get(FIELD_USER_ID);
    if (upstream == null || userId == null) {
      return null;
    }
    try {
      return new PortalSessionRecord(sessionId, upstream.toString(), Long.parseLong(userId.toString()));
    } catch (IllegalArgumentException e) {
      // 记录损坏：按无效会话处理，不放行。
      log.warn("event=session_record_invalid");
      return null;
    }
  }

  @Override
  public boolean delete(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return false;
    }
    return Boolean.TRUE.equals(redis.delete(KEY_PREFIX + sessionId));
  }

  private String newIdentifier() {
    byte[] bytes = new byte[SESSION_ID_BYTES];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}