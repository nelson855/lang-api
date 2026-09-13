package com.lang.portal.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalPropertiesValidatorTests {

  @Test
  void enabledProtocolWithoutUrlFails() {
    assertThatThrownBy(() ->
            PortalPropertiesValidator.validatePublicConfig("Lang API", List.of("OPENAI"), Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("缺少合法公开地址");
  }

  @Test
  void baseUrlWithQueryFails() {
    assertThatThrownBy(() ->
            PortalPropertiesValidator.validateBaseUrl("http://new-api:3000/x?q=1", "k"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyOpenaiProtocolAcceptedInP107() {
    assertThatThrownBy(() ->
            PortalPropertiesValidator.validatePublicConfig(
                "Lang API", List.of("ANTHROPIC"), Map.of("ANTHROPIC", "https://api.example.com/v1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OPENAI");
  }

  @Test
  void openaiUrlMustEndWithV1() {
    assertThatThrownBy(() ->
            PortalPropertiesValidator.validatePublicConfig(
                "Lang API", List.of("OPENAI"), Map.of("OPENAI", "http://api.localhost:8081/v1beta")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/v1");
  }

  @Test
  void openaiV1UrlPasses() {
    PortalPropertiesValidator.validatePublicConfig(
        "Lang API", List.of("OPENAI"), Map.of("OPENAI", "http://api.localhost:8081/v1"));
  }
}
