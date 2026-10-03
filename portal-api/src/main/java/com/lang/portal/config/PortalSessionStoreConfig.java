package com.lang.portal.config;

import com.lang.portal.infrastructure.session.InMemoryPortalSessionStore;
import com.lang.portal.infrastructure.session.PortalSessionStore;
import com.lang.portal.infrastructure.session.RedisPortalSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PortalSessionStoreConfig {

  /**
   * 生产权威实现：Redis 记录由所有实例共享，撤销立即生效，重启后已删除的记录不会复活。
   */
  @Bean
  @ConditionalOnMissingBean(PortalSessionStore.class)
  @ConditionalOnProperty(name = "lang.auth.session.store", havingValue = "redis", matchIfMissing = true)
  public PortalSessionStore redisPortalSessionStore(
      StringRedisTemplate redisTemplate, PortalCommonProperties properties) {
    return new RedisPortalSessionStore(redisTemplate, properties.auth().session().ttl());
  }

  /**
   * 仅供自动化测试与单机本地开发：记录不跨进程共享，重启即失效，不能用于多实例部署。
   */
  @Bean
  @ConditionalOnMissingBean(PortalSessionStore.class)
  @ConditionalOnProperty(name = "lang.auth.session.store", havingValue = "memory")
  public PortalSessionStore inMemoryPortalSessionStore(PortalCommonProperties properties) {
    return new InMemoryPortalSessionStore(properties.auth().session().ttl());
  }
}