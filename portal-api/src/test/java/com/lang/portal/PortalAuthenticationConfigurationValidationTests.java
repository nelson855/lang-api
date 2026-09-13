package com.lang.portal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class PortalAuthenticationConfigurationValidationTests {

  @Test
  void productionStartupRejectsMissingAllowedOrigins() {
    assertThatThrownBy(() -> {
          try (var context = new SpringApplicationBuilder(LangApiApplication.class)
              .web(WebApplicationType.SERVLET)
              .profiles("prod")
              .run(
                  "--server.port=0",
                  "--lang.upstream.new-api.base-url=http://127.0.0.1:3000",
                  "--lang.portal.site-name=Lang API",
                  "--lang.auth.trusted-proxy.cidrs=10.0.0.0/8")) {
          }
        })
        .hasRootCauseMessage("非法配置 lang.auth.allowed-origins：生产环境不能为空");
  }

  @Test
  void productionStartupRejectsMissingTrustedProxyCidrs() {
    assertThatThrownBy(() -> {
          try (var context = new SpringApplicationBuilder(LangApiApplication.class)
              .web(WebApplicationType.SERVLET)
              .profiles("prod")
              .run(
                  "--server.port=0",
                  "--lang.upstream.new-api.base-url=http://127.0.0.1:3000",
                  "--lang.portal.site-name=Lang API",
                  "--lang.auth.allowed-origins=https://portal.example")) {
          }
        })
        .hasRootCauseMessage("非法配置 lang.auth.trusted-proxy-cidrs：生产环境不能为空");
  }

  @Test
  void startupRejectsAuthenticationCookieDomainConfiguration() {
    assertThatThrownBy(() -> {
          try (var context = new SpringApplicationBuilder(LangApiApplication.class)
              .web(WebApplicationType.SERVLET)
              .profiles("dev")
              .run("--server.port=0", "--lang.auth.cookie.domain=example.com")) {
          }
        })
        .hasRootCauseMessage("非法配置 lang.auth.cookie.domain：不支持配置 Cookie Domain");
  }

}
