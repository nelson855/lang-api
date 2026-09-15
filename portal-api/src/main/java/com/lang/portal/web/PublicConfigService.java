package com.lang.portal.web;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicSiteProperties;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.web.dto.ApiBaseUrl;
import com.lang.portal.web.dto.PublicConfigResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class PublicConfigService {

  private static final List<String> ORDER = List.of("OPENAI", "ANTHROPIC", "GEMINI");
  private static final Set<String> KNOWN_LOCALES = Set.of("zh-CN", "en-US");

  private final PortalCommonProperties properties;
  private final PublicationProperties publication;
  private final PublicSiteProperties publicSite;

  public PublicConfigService(
      PortalCommonProperties properties,
      PublicationProperties publication,
      PublicSiteProperties publicSite) {
    this.properties = properties;
    this.publication = publication;
    this.publicSite = publicSite;
  }

  public PublicConfigResponse current() {
    Map<String, String> urls = properties.portal().urlMap();
    List<String> enabled = properties.portal().enabledProtocols().stream()
        .map(s -> s.trim().toUpperCase(Locale.ROOT))
        .toList();
    List<ApiBaseUrl> result = new ArrayList<>();
    for (String protocol : ORDER) {
      if (enabled.contains(protocol) && urls.containsKey(protocol)) {
        result.add(new ApiBaseUrl(protocol, urls.get(protocol)));
      }
    }
    return new PublicConfigResponse(
        properties.portal().siteName(),
        publication.mode().name(),
        normalizeUrl(publicSite.siteUrl()),
        normalizeUrl(publicSite.supportUrl()),
        normalizeRegions(publicSite.supportedRegions()),
        normalizeLocales(publicSite.enabledLocales()),
        List.copyOf(result));
  }

  private static String normalizeUrl(String value) {
    return value == null ? "" : value.trim();
  }

  private static List<String> normalizeRegions(List<String> regions) {
    if (regions == null) {
      return List.of();
    }
    Set<String> sorted = new TreeSet<>();
    for (String region : regions) {
      if (region == null) {
        continue;
      }
      String value = region.trim().toUpperCase(Locale.ROOT);
      if (value.matches("[A-Z]{2}")) {
        sorted.add(value);
      }
    }
    return List.copyOf(sorted);
  }

  private static List<String> normalizeLocales(List<String> locales) {
    if (locales == null) {
      return List.of();
    }
    Set<String> ordered = new LinkedHashSet<>();
    for (String locale : locales) {
      if (locale != null && KNOWN_LOCALES.contains(locale.trim()) && !ordered.contains(locale.trim())) {
        ordered.add(locale.trim());
      }
    }
    return List.copyOf(ordered);
  }
}
