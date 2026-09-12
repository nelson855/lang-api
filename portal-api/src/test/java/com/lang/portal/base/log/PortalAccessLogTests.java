package com.lang.portal.base.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalAccessLogTests {

  @Autowired private MockMvc mvc;

  @Test
  void successProducesSingleCompletedEventWithoutQuery() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(PortalAccessLogFilter.class);
    ch.qos.logback.classic.Level previous = logger.getLevel();
    logger.setLevel(ch.qos.logback.classic.Level.INFO);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      MvcResult result = mvc.perform(get("/portal/api/public-config?token=secret123&email=a@b.com")).andReturn();
      String requestId = result.getResponse().getHeader("X-Request-Id");
      assertThat(appender.list).hasSize(1);
      String message = appender.list.get(0).getFormattedMessage();
      assertThat(message).contains("event=portal_access");
      assertThat(message).contains("requestId=" + requestId);
      assertThat(message).contains("method=GET").contains("status=200").contains("durationMs=");
      assertThat(message).doesNotContain("secret123").doesNotContain("a@b.com");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previous);
    }
  }
}
