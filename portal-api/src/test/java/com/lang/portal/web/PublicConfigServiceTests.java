package com.lang.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.config.PortalCommonProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicConfigServiceTests {

  private PortalCommonProperties props(String site, List<String> enabled, String openai) {
    PortalCommonProperties p = new PortalCommonProperties();
    p.portal().setSiteName(site);
    p.portal().setEnabledProtocols(enabled);
    p.portal().publicUrls().setOpenai(openai);
    return p;
  }

  @Test
  void keepsFixedOrderAndOnlyEnabledLegal() {
    PortalCommonProperties p = props("Lang API", List.of("GEMINI", "OPENAI"), "https://api.example.com/v1");
    p.portal().publicUrls().setGemini("https://gemini.example.com/v1");
    PublicConfigService service = new PublicConfigService(p);
    var response = service.current();
    assertThat(response.apiBaseUrls()).extracting("protocol").containsExactly("OPENAI", "GEMINI");
  }

  @Test
  void emptyWhenNothingEnabled() {
    PublicConfigService service = new PublicConfigService(props("Lang API", List.of(), "https://api.example.com/v1"));
    assertThat(service.current().apiBaseUrls()).isEmpty();
  }
}
