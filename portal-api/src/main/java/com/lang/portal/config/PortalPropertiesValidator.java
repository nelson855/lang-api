package com.lang.portal.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
public class PortalPropertiesValidator {

  private final PortalCommonProperties properties;
  private final Environment environment;

  public PortalPropertiesValidator(PortalCommonProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    validateBaseUrl(properties.upstream().newApi().baseUrl(), "lang.upstream.new-api.base-url");
    validateRequestId(properties.request().requestId().minLength(), properties.request().requestId().maxLength());
    validateApiKey(properties.apiKey());
    validatePublicConfig(properties.portal().siteName(), properties.portal().enabledProtocols(), properties.portal().urlMap());
    if (environment.containsProperty("lang.auth.cookie.domain")) {
      throw new IllegalStateException("非法配置 lang.auth.cookie.domain：不支持配置 Cookie Domain");
    }
    validateAuthentication(properties.env(), properties.auth());
  }

  static void validateBaseUrl(String value, String key) {
    URI uri = toAbsoluteHttpUri(value, key);
    if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalStateException("非法配置 " + key + "：不得包含用户信息、查询串或片段");
    }
  }

  static void validatePublicConfig(String siteName, List<String> enabled, Map<String, String> urls) {
    if (siteName == null || siteName.isBlank()) {
      throw new IllegalStateException("非法配置 lang.portal.site-name：不能为空");
    }
    Set<String> known = Set.of("OPENAI");
    for (String raw : enabled) {
      String protocol = raw.trim().toUpperCase(Locale.ROOT);
      if (!known.contains(protocol)) {
        throw new IllegalStateException(
            "非法配置 lang.portal.enabled-protocols：LANG-P1-07 当前只接受 OPENAI，未知协议 " + raw);
      }
      String url = urls.get(protocol);
      if (url == null || url.isBlank()) {
        throw new IllegalStateException("非法配置：已启用协议 " + protocol + " 缺少合法公开地址");
      }
      URI uri = toAbsoluteHttpUri(url, "lang.portal.public-urls." + protocol.toLowerCase(Locale.ROOT));
      if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
        throw new IllegalStateException("非法配置公开地址：不得包含用户信息、查询串或片段");
      }
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (!path.endsWith("/v1")) {
        throw new IllegalStateException("非法配置公开地址：必须以 /v1 结束");
      }
    }
    for (Map.Entry<String, String> entry : urls.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isBlank()) {
        continue;
      }
      toAbsoluteHttpUri(entry.getValue(), "lang.portal.public-urls." + entry.getKey().toLowerCase(Locale.ROOT));
    }
  }

  static void validateAuthentication(String environment, PortalCommonProperties.Auth auth) {
    if (!"prod".equalsIgnoreCase(environment)) {
      return;
    }
    if (!auth.cookie().secure()) {
      throw new IllegalStateException("非法配置 lang.auth.cookie.secure：生产环境必须启用 Secure");
    }
    if (!auth.cookie().sessionName().matches("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+")) {
      throw new IllegalStateException("非法配置 lang.auth.cookie.session-name：必须是合法 Cookie 名");
    }
    if (!auth.cookie().userIdName().matches("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+")) {
      throw new IllegalStateException("非法配置 lang.auth.cookie.user-id-name：必须是合法 Cookie 名");
    }
    if (auth.rateLimit().login().usernameAttempts() > 20) {
      throw new IllegalStateException("非法配置 lang.auth.rate-limit.login.username-attempts：不得超过 20");
    }
    if (auth.rateLimit().login().window().isZero() || auth.rateLimit().login().window().isNegative()) {
      throw new IllegalStateException("非法配置 lang.auth.rate-limit.login.window：必须为正数");
    }
    if (auth.allowedOrigins() == null || auth.allowedOrigins().isBlank()) {
      throw new IllegalStateException("非法配置 lang.auth.allowed-origins：生产环境不能为空");
    }
    if (auth.trustedProxy().cidrs() == null || auth.trustedProxy().cidrs().isBlank()) {
      throw new IllegalStateException("非法配置 lang.auth.trusted-proxy-cidrs：生产环境不能为空");
    }
  }

  private static void validateRequestId(int min, int max) {
    if (min < 1 || max < min || max > 128) {
      throw new IllegalStateException("非法配置 requestId 长度边界");
    }
  }

  static void validateApiKey(PortalCommonProperties.ApiKey apiKey) {
    if (apiKey.defaultPageSize() < 1 || apiKey.maxPageSize() < 1) {
      throw new IllegalStateException("非法配置 lang.api-key.page-size：必须为正数");
    }
    if (apiKey.defaultPageSize() > apiKey.maxPageSize()) {
      throw new IllegalStateException("非法配置 lang.api-key.default-page-size：默认值不得大于最大值");
    }
    if (apiKey.maxPageSize() > 100) {
      throw new IllegalStateException("非法配置 lang.api-key.max-page-size：不得超过 100");
    }
    if (apiKey.statusAggregationMaxPages() < 1 || apiKey.statusAggregationMaxPages() > 100) {
      throw new IllegalStateException("非法配置 lang.api-key.status-aggregation-max-pages：必须在 1～100 之间");
    }
    if (apiKey.revealTtl() == null || apiKey.revealTtl().isZero() || apiKey.revealTtl().isNegative()) {
      throw new IllegalStateException("非法配置 lang.api-key.reveal-ttl：必须为正数");
    }
  }

  private static URI toAbsoluteHttpUri(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("非法配置 " + key + "：不能为空");
    }
    URI uri;
    try {
      uri = new URI(value.trim());
    } catch (Exception e) {
      throw new IllegalStateException("非法配置 " + key + "：不是合法地址");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
      throw new IllegalStateException("非法配置 " + key + "：必须是绝对 HTTP(S) 地址");
    }
    return uri;
  }
}
