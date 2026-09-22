package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AggregationProtectionLogTests {

  private ListAppender<ILoggingEvent> attach() {
    Logger logger = (Logger) LoggerFactory.getLogger(AggregationProtectionLog.class);
    logger.setLevel(Level.WARN);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private void detach(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(AggregationProtectionLog.class);
    logger.detachAppender(appender);
  }

  private String output(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
  }

  @Test
  void protectionWarningCarriesRequestIdAndFiniteReasonOnly() {
    ListAppender<ILoggingEvent> appender = attach();
    try {
      AggregationProtectionLog.warn("req-abc-123", "usage-summary", ProtectReason.RECORDS);

      String log = output(appender);
      assertThat(log)
          .contains("event=aggregation_protection")
          .contains("op=usage-summary")
          .contains("reason=records")
          .contains("requestId=req-abc-123");
      assertThat(log)
          .doesNotContain(
              "user-42",
              "upstream-session",
              "sk-secret-key",
              "原始上游响应正文",
              "gpt-test",
              "probe-key-01");
    } finally {
      detach(appender);
    }
  }

  @Test
  void allFiniteReasonsAreLoggable() {
    ListAppender<ILoggingEvent> appender = attach();
    try {
      for (ProtectReason reason : ProtectReason.values()) {
        AggregationProtectionLog.warn("req-abc-123", "usage-summary", reason);
      }
      assertThat(appender.list).hasSize(ProtectReason.values().length);
    } finally {
      detach(appender);
    }
  }
}
