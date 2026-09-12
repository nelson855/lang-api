package com.lang.portal.web;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.web.dto.ApiBaseUrl;
import com.lang.portal.web.dto.PublicConfigResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PublicConfigService {

  private static final List<String> ORDER = List.of("OPENAI", "ANTHROPIC", "GEMINI");

  private final PortalCommonProperties properties;

  public PublicConfigService(PortalCommonProperties properties) {
    this.properties = properties;
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
    return new PublicConfigResponse(properties.portal().siteName(), List.copyOf(result));
  }
}
