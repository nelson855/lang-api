package com.lang.portal.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 针对真实 Redis 的存储契约：跨"实例"共享、删除后不复活、TTL 到期。
 *
 * <p>这里刻意不用进程内实现代替——撤销能否跨实例生效、重启后是否复活，只有真实共享存储才能证明。
 * 未配置 {@code -Dlang.test.redis.host} 时整体跳过，不以内存实现冒充通过。
 */
class RedisPortalSessionStoreTests {

  private static final String PREFIX = "lang:portal:session:";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;
  private RedisPortalSessionStore store;
  private String host;
  private int port;
  private String password;

  @BeforeEach
  void connect() {
    host = System.getProperty("lang.test.redis.host");
    Assumptions.assumeTrue(host != null && !host.isBlank(),
        "未提供 lang.test.redis.host，跳过真实 Redis 会话存储验证");
    port = Integer.getInteger("lang.test.redis.port", 6379);
    password = System.getProperty("lang.test.redis.password");
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
    if (password != null && !password.isBlank()) {
      configuration.setPassword(password);
    }
    connectionFactory = new LettuceConnectionFactory(configuration);
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
    store = new RedisPortalSessionStore(redis, Duration.ofMinutes(30));
  }

  @AfterEach
  void disconnect() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void everyLoginGetsAnIndependentSessionThatHidesTheUpstreamValue() {
    String first = store.create("upstream-a", 42L);
    String second = store.create("upstream-a", 42L);

    assertThat(first).isNotEqualTo(second);
    assertThat(first).doesNotContain("upstream-a");
    assertThat(store.find(first)).isEqualTo(new PortalSessionRecord(first, "upstream-a", 42L));
    assertThat(store.find(second)).isEqualTo(new PortalSessionRecord(second, "upstream-a", 42L));

    redis.delete(PREFIX + first);
    redis.delete(PREFIX + second);
  }

  @Test
  void aSecondStoreInstanceSeesAndRevokesTheSameRecord() {
    // 模拟另一个 Lang API 实例：独立连接、独立对象，共享同一份 Redis 记录。
    RedisPortalSessionStore otherInstance = new RedisPortalSessionStore(redis, Duration.ofMinutes(30));
    String id = store.create("upstream-a", 42L);

    assertThat(otherInstance.find(id)).isNotNull();

    assertThat(store.delete(id)).isTrue();

    // 撤销对其他实例立即生效，无需等待任何同步。
    assertThat(otherInstance.find(id)).isNull();
    assertThat(otherInstance.delete(id)).isFalse();
  }

  @Test
  void aRecordDeletedByOneInstanceStaysGoneForAFreshConnection() {
    // 模拟重启：新连接、新对象，按旧 Cookie 查询不得让记录复活。
    String id = store.create("upstream-a", 42L);
    store.delete(id);

    LettuceConnectionFactory afterRestart =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    if (password != null && !password.isBlank()) {
      afterRestart.setPassword(password);
    }
    afterRestart.afterPropertiesSet();
    afterRestart.start();
    try {
      StringRedisTemplate freshRedis = new StringRedisTemplate(afterRestart);
      freshRedis.afterPropertiesSet();
      RedisPortalSessionStore afterRestartStore = new RedisPortalSessionStore(freshRedis, Duration.ofMinutes(30));

      assertThat(afterRestartStore.find(id)).isNull();
    } finally {
      afterRestart.destroy();
    }
  }

  @Test
  void recordsExpireOnTheirOwnAndAreNoLongerFound() throws Exception {
    RedisPortalSessionStore shortLived = new RedisPortalSessionStore(redis, Duration.ofSeconds(2));
    String id = shortLived.create("upstream-a", 42L);
    assertThat(shortLived.find(id)).isNotNull();

    Thread.sleep(3_500L);

    assertThat(shortLived.find(id)).isNull();
  }

  @Test
  void anUnknownIdentifierIsSimplyAbsent() {
    assertThat(store.find("never-issued")).isNull();
    assertThat(store.delete("never-issued")).isFalse();
  }

  @Test
  void recordsOfDifferentUsersNeverCollide() {
    String first = store.create("upstream-a", 42L);
    String second = store.create("upstream-b", 7L);

    store.delete(first);

    assertThat(store.find(first)).isNull();
    assertThat(store.find(second)).isEqualTo(new PortalSessionRecord(second, "upstream-b", 7L));

    redis.delete(PREFIX + second);
  }

  @Test
  void storageKeysAreNamespacedAwayFromOtherApplications() {
    String id = store.create("upstream-a", 42L);

    assertThat(redis.keys(PREFIX + "*")).contains(PREFIX + id);

    redis.delete(PREFIX + id);
  }
}