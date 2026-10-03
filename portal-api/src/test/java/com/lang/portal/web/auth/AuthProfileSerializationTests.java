package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuthProfileSerializationTests {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void unsetUpstreamEmailSerializesAsExplicitNullForBrowserSessionRestoration() throws Exception {
    for (String email : new String[] {null, "", "  "}) {
      var json = mapper.readTree(mapper.writeValueAsString(new AuthProfile(2, "probe", "Probe", email)));
      assertThat(json.has("email")).isTrue();
      assertThat(json.get("email").isNull()).isTrue();
      assertThat(json.get("username").asText()).isEqualTo("probe");
    }
  }

  @Test
  void populatedEmailIsPreserved() {
    assertThat(new AuthProfile(2, "probe", "Probe", "probe@example.test").email())
        .isEqualTo("probe@example.test");
  }
}
