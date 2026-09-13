package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lang.portal.base.log.PortalAccessLogFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeySecurityBaselineTests {

  @Autowired private MockMvc mvc;

  @Test
  void anonymousListAndDetailRequireSession() throws Exception {
    mvc.perform(get("/portal/api/api-keys"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    mvc.perform(get("/portal/api/api-keys/7"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void forgedUpstreamIdentityHeaderIsIgnoredWithoutSession() throws Exception {
    mvc.perform(get("/portal/api/api-keys/7").header("New-Api-User", "999"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  @WithMockUser
  void mockAuthenticationAloneGrantsNoKeyAccess() throws Exception {
    mvc.perform(get("/portal/api/api-keys"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void accessLogUsesRoutePatternWithoutQuery() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(PortalAccessLogFilter.class);
    Level previous = logger.getLevel();
    logger.setLevel(Level.INFO);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mvc.perform(get("/portal/api/api-keys")
              .param("name", "probe-secret")
              .param("status", "enabled"))
          .andExpect(status().isUnauthorized());
      assertThat(appender.list).hasSize(1);
      String message = appender.list.get(0).getFormattedMessage();
      assertThat(message).contains("route=/portal/api/api-keys ");
      assertThat(message).doesNotContain("probe-secret").doesNotContain("?");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previous);
    }
  }

  @Test
  void keyErrorResponsesAreJson() throws Exception {
    mvc.perform(get("/portal/api/api-keys/abc"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
}
