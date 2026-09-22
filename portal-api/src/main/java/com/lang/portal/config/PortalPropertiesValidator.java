package com.lang.portal.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
public class PortalPropertiesValidator {

  private final PortalCommonProperties properties;
  private final PublicationProperties publication;
  private final PublicSiteProperties publicSite;
  private final LegalContentProperties legalContent;
  private final Environment environment;

  public PortalPropertiesValidator(
      PortalCommonProperties properties,
      PublicationProperties publication,
      PublicSiteProperties publicSite,
      LegalContentProperties legalContent,
      Environment environment) {
    this.properties = properties;
    this.publication = publication;
    this.publicSite = publicSite;
    this.legalContent = legalContent;
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    validateBaseUrl(properties.upstream().newApi().baseUrl(), "lang.upstream.new-api.base-url");
    validateRequestId(properties.request().requestId().minLength(), properties.request().requestId().maxLength());
    validateApiKey(properties.apiKey());
    validateCatalog(properties.catalog());
    validateUsage(properties.portal().usage());
    validateAggregation(properties.aggregation());
    validatePublicConfig(properties.portal().siteName(), properties.portal().enabledProtocols(), properties.portal().urlMap());
    validatePublication(publication, publicSite, legalContent);
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
    if (auth.rateLimit().profileUpdate().userAttempts() > 20) {
      throw new IllegalStateException("非法配置 lang.auth.rate-limit.profile-update.user-attempts：不得超过 20");
    }
    if (auth.rateLimit().profileUpdate().window().isZero() || auth.rateLimit().profileUpdate().window().isNegative()) {
      throw new IllegalStateException("非法配置 lang.auth.rate-limit.profile-update.window：必须为正数");
    }
    if (auth.allowedOrigins() == null || auth.allowedOrigins().isBlank()) {
      throw new IllegalStateException("非法配置 lang.auth.allowed-origins：生产环境不能为空");
    }
    if (auth.trustedProxy().cidrs() == null || auth.trustedProxy().cidrs().isBlank()) {
      throw new IllegalStateException("非法配置 lang.auth.trusted-proxy-cidrs：生产环境不能为空");
    }
  }

  static void validatePublication(
      PublicationProperties publication, PublicSiteProperties site, LegalContentProperties legal) {
    if (publication.mode() == null) {
      throw new IllegalStateException("非法配置 lang.publication.mode：只允许 PREVIEW 或 PUBLIC");
    }
    validateLegalContent(legal);
    validateOptionalSiteUrl(site.siteUrl(), "lang.public.site-url", publication.mode() == PublicationMode.PUBLIC);
    validateSupportUrl(site.supportUrl(), publication.mode() == PublicationMode.PUBLIC);
    validateRegions(site.supportedRegions(), publication.mode() == PublicationMode.PUBLIC);
    validateLocales(site.enabledLocales(), legal.sourceLocale(), publication.mode() == PublicationMode.PUBLIC);
  }

  private static void validateLegalContent(LegalContentProperties legal) {
    if (!Set.of("zh-CN", "en-US").contains(legal.sourceLocale())) {
      throw new IllegalStateException("非法配置 lang.legal.source-locale：只允许 zh-CN 或 en-US");
    }
    if (legal.sourceFormat() == null) {
      throw new IllegalStateException("非法配置 lang.legal.source-format：只允许 MARKDOWN 或 HTML");
    }
    if (legal.maxInputBytes() < 16 * 1024L || legal.maxInputBytes() > 1024 * 1024L) {
      throw new IllegalStateException("非法配置 lang.legal.max-input-bytes：必须在 16384～1048576 之间");
    }
    if (legal.maxOutputBytes() < 16 * 1024L || legal.maxOutputBytes() > 1024 * 1024L) {
      throw new IllegalStateException("非法配置 lang.legal.max-output-bytes：必须在 16384～1048576 之间");
    }
    if (legal.cacheTtl() == null
        || legal.cacheTtl().compareTo(java.time.Duration.ofSeconds(10)) < 0
        || legal.cacheTtl().compareTo(java.time.Duration.ofHours(1)) > 0) {
      throw new IllegalStateException("非法配置 lang.legal.cache-ttl：必须在 10 秒到 1 小时之间");
    }
  }

  private static void validateOptionalSiteUrl(String value, String key, boolean required) {
    if (value == null || value.isBlank()) {
      if (required) {
        throw new IllegalStateException("非法配置 " + key + "：PUBLIC 模式不能为空");
      }
      return;
    }
    URI uri = toAbsoluteHttpUri(value, key);
    if (!"https".equalsIgnoreCase(uri.getScheme()) && required) {
      throw new IllegalStateException("非法配置 " + key + "：PUBLIC 模式必须使用 HTTPS");
    }
    if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalStateException("非法配置 " + key + "：不得包含用户信息、查询串或片段");
    }
  }

  private static void validateSupportUrl(String value, boolean required) {
    if (value == null || value.isBlank()) {
      if (required) {
        throw new IllegalStateException("非法配置 lang.public.support-url：PUBLIC 模式不能为空");
      }
      return;
    }
    URI uri;
    try {
      uri = new URI(value.trim());
    } catch (Exception e) {
      throw new IllegalStateException("非法配置 lang.public.support-url：不是合法地址");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!("https".equals(scheme) || "mailto".equals(scheme))
        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
        || ("https".equals(scheme) && uri.getHost() == null)
        || ("mailto".equals(scheme) && (uri.getSchemeSpecificPart() == null || uri.getSchemeSpecificPart().isBlank()))) {
      throw new IllegalStateException("非法配置 lang.public.support-url：只允许安全 HTTPS 或 mailto 地址");
    }
  }

  private static void validateRegions(List<String> regions, boolean required) {
    if (regions == null || regions.isEmpty()) {
      if (required) {
        throw new IllegalStateException("非法配置 lang.public.supported-regions：PUBLIC 模式至少需要一个地区");
      }
      return;
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String region : regions) {
      String value = region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
      if (!value.matches("[A-Z]{2}") || !normalized.add(value)) {
        throw new IllegalStateException("非法配置 lang.public.supported-regions：必须是去重的 ISO 3166-1 alpha-2 代码");
      }
    }
  }

  private static void validateLocales(List<String> locales, String sourceLocale, boolean publicMode) {
    if (locales == null || locales.isEmpty()) {
      if (publicMode) {
        throw new IllegalStateException("非法配置 lang.public.enabled-locales：PUBLIC 模式必须启用法律源语言");
      }
      return;
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String locale : locales) {
      if (!Set.of("zh-CN", "en-US").contains(locale) || !normalized.add(locale)) {
        throw new IllegalStateException("非法配置 lang.public.enabled-locales：只允许去重的 zh-CN、en-US");
      }
    }
    if (publicMode && (normalized.size() != 1 || !normalized.contains(sourceLocale))) {
      throw new IllegalStateException("非法配置 lang.public.enabled-locales：PUBLIC 模式只能启用 legal.source-locale");
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

  static void validateCatalog(PortalCommonProperties.Catalog catalog) {
    if (catalog.quotaPerUsd() <= 0) {
      throw new IllegalStateException("非法配置 lang.portal.catalog.quota-per-usd：必须为正整数");
    }
    if (catalog.cacheTtl() == null || catalog.cacheTtl().isZero() || catalog.cacheTtl().isNegative()) {
      throw new IllegalStateException("非法配置 lang.portal.catalog.cache-ttl：必须为正数");
    }
  }

  static void validateUsage(PortalCommonProperties.Usage usage) {
    if (usage.defaultRange() == null
        || usage.defaultRange().isZero()
        || usage.defaultRange().isNegative()) {
      throw new IllegalStateException("非法配置 lang.portal.usage.default-range：必须为正数");
    }
    if (usage.maxRange() == null || usage.maxRange().isZero() || usage.maxRange().isNegative()) {
      throw new IllegalStateException("非法配置 lang.portal.usage.max-range：必须为正数");
    }
    if (usage.defaultRange().compareTo(usage.maxRange()) > 0) {
      throw new IllegalStateException("非法配置 lang.portal.usage.default-range：默认值不得大于最大值");
    }
    if (usage.defaultPageSize() < 1 || usage.maxPageSize() < 1) {
      throw new IllegalStateException("非法配置 lang.portal.usage.page-size：必须为正数");
    }
    if (usage.defaultPageSize() > usage.maxPageSize()) {
      throw new IllegalStateException("非法配置 lang.portal.usage.default-page-size：默认值不得大于最大值");
    }
    if (usage.maxPageSize() > 100) {
      throw new IllegalStateException("非法配置 lang.portal.usage.max-page-size：不得超过 100");
    }
  }

  static void validateAggregation(PortalCommonProperties.Aggregation aggregation) {
    if (!"p2-2026-09-22-a".equals(aggregation.baselineVersion())) {
      throw new IllegalStateException(
          "非法配置 lang.aggregation.baseline-version：不受支持的口径版本 " + aggregation.baselineVersion());
    }
    if (aggregation.pageSize() < 1 || aggregation.maxPages() < 1 || aggregation.maxRecords() < 1) {
      throw new IllegalStateException("非法配置 lang.aggregation.max-records：页数与记录数必须为正数");
    }
    if (aggregation.maxRecords() < aggregation.pageSize()) {
      throw new IllegalStateException("非法配置 lang.aggregation.max-records：最大记录数不得小于单页大小");
    }
    long capacity = (long) aggregation.pageSize() * (long) aggregation.maxPages();
    if (aggregation.maxRecords() > capacity) {
      throw new IllegalStateException("非法配置 lang.aggregation.max-records：最大记录数不得超过单页大小与最大页数乘积");
    }
    if (aggregation.singleCallTimeout() == null
        || aggregation.singleCallTimeout().isZero()
        || aggregation.singleCallTimeout().isNegative()) {
      throw new IllegalStateException("非法配置 lang.aggregation.single-call-timeout：必须为正数");
    }
    if (aggregation.totalTimeout() == null
        || aggregation.totalTimeout().isZero()
        || aggregation.totalTimeout().isNegative()) {
      throw new IllegalStateException("非法配置 lang.aggregation.total-timeout：必须为正数");
    }
    if (aggregation.totalTimeout().compareTo(aggregation.singleCallTimeout()) < 0) {
      throw new IllegalStateException("非法配置 lang.aggregation.total-timeout：总预算不得短于单次调用超时");
    }
    if (aggregation.maxLiveLogRange() == null
        || aggregation.maxLiveLogRange().isZero()
        || aggregation.maxLiveLogRange().isNegative()) {
      throw new IllegalStateException("非法配置 lang.aggregation.max-live-log-range：必须为正数");
    }
    if (aggregation.cacheTtl() == null
        || aggregation.cacheTtl().isZero()
        || aggregation.cacheTtl().isNegative()) {
      throw new IllegalStateException("非法配置 lang.aggregation.cache.ttl：必须为正数");
    }
    if (aggregation.cacheMaximumSize() < 1) {
      throw new IllegalStateException("非法配置 lang.aggregation.cache.maximum-size：必须为正数");
    }
    if (aggregation.fiveMinutesMaxSpan() == null
        || aggregation.fiveMinutesMaxSpan().isZero()
        || aggregation.fiveMinutesMaxSpan().isNegative()
        || aggregation.hourMaxSpan() == null
        || aggregation.hourMaxSpan().isZero()
        || aggregation.hourMaxSpan().isNegative()
        || aggregation.dayMaxSpan() == null
        || aggregation.dayMaxSpan().isZero()
        || aggregation.dayMaxSpan().isNegative()) {
      throw new IllegalStateException("非法配置 lang.aggregation.granularity：粒度跨度必须为正数");
    }
    if (aggregation.fiveMinutesMaxSpan().compareTo(aggregation.hourMaxSpan()) > 0
        || aggregation.hourMaxSpan().compareTo(aggregation.dayMaxSpan()) > 0) {
      throw new IllegalStateException("非法配置 lang.aggregation.granularity：粒度跨度必须单调递增");
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
