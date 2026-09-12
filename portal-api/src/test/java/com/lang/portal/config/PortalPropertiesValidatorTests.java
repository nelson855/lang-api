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
}
