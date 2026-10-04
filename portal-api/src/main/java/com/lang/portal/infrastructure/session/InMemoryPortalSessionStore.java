package com.lang.portal.infrastructure.session;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储，仅用于单元测试与单实例本地开发。
 *
 * <p>记录不跨进程共享，也不能在重启后保留；生产环境必须使用 {@link RedisPortalSessionStore}。
 */
public class InMemoryPortalSessionStore implements PortalSessionStore {

  private static final int SESSION_ID_BYTES = 32;

  private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
  private final Duration ttl;
  private final SecureRandom random = new SecureRandom();
  private final java.util.function.LongSupplier clock;

  public InMemoryPortalSessionStore(Duration ttl) {
    this(ttl, System::currentTimeMillis);
  }

  public InMemoryPortalSessionStore(Duration ttl, java.util.function.LongSupplier clock) {
    this.ttl = ttl;
    this.clock = clock;
  }

  @Override
  public String create(String upstreamSessionValue, long userId) {
    String sessionId = newIdentifier();
    sessions.put(sessionId, new Entry(new PortalSessionRecord(sessionId, upstreamSessionValue, userId), expiry()));
    return sessionId;
  }

  @Override
  public PortalSessionRecord find(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    Entry entry = sessions.get(sessionId);
    if (entry == null) {
      return null;
    }
    if (entry.expiresAtMillis() <= clock.getAsLong()) {
      sessions.remove(sessionId, entry);
      return null;
    }
    return entry.record();
  }

  @Override
  public boolean delete(String sessionId) {
    return sessionId != null && sessions.remove(sessionId) != null;
  }

  private long expiry() {
    return clock.getAsLong() + ttl.toMillis();
  }

  private String newIdentifier() {
    byte[] bytes = new byte[SESSION_ID_BYTES];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private record Entry(PortalSessionRecord record, long expiresAtMillis) {}
}