package com.lang.portal.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PortalPropertiesValidator {

  private final PortalCommonProperties properties;

  public PortalPropertiesValidator(PortalCommonProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void validate() {
    validateBaseUrl(properties.upstream().newApi().baseUrl(), "lang.upstream.new-api.base-url");
    validateRequestId(properties.request().requestId().minLength(), properties.request().requestId().maxLength());
    validatePublicConfig(properties.portal().siteName(), properties.portal().enabledProtocols(), properties.portal().urlMap());
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
    Set<String> known = Set.of("OPENAI", "ANTHROPIC", "GEMINI");
    for (String raw : enabled) {
      String protocol = raw.trim().toUpperCase(Locale.ROOT);
      if (!known.contains(protocol)) {
        throw new IllegalStateException("非法配置 lang.portal.enabled-protocols：未知协议 " + raw);
      }
      String url = urls.get(protocol);
      if (url == null || url.isBlank()) {
        throw new IllegalStateException("非法配置：已启用协议 " + protocol + " 缺少合法公开地址");
      }
      URI uri = toAbsoluteHttpUri(url, "lang.portal.public-urls." + protocol.toLowerCase(Locale.ROOT));
      if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
        throw new IllegalStateException("非法配置公开地址：不得包含用户信息、查询串或片段");
      }
    }
    for (Map.Entry<String, String> entry : urls.entrySet()) {
      if (entry.getValue() == null || entry.getValue().isBlank()) {
        continue;
      }
      toAbsoluteHttpUri(entry.getValue(), "lang.portal.public-urls." + entry.getKey().toLowerCase(Locale.ROOT));
    }
  }

  private static void validateRequestId(int min, int max) {
    if (min < 1 || max < min || max > 128) {
      throw new IllegalStateException("非法配置 requestId 长度边界");
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
