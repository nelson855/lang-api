package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.SensitiveSecret;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApiKeySecurityEventsTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private final NewApiTokenClient client = mock(NewApiTokenClient.class);
  private final ApiKeySecurityEvents events = new ApiKeySecurityEvents();
  private final ApiKeyApplicationService service = new ApiKeyApplicationService(client, events);

  private ListAppender<ILoggingEvent> attach() {
    Logger logger = (Logger) LoggerFactory.getLogger(ApiKeySecurityEvents.class);
    logger.setLevel(ch.qos.logback.classic.Level.INFO);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private void detach(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(ApiKeySecurityEvents.class);
    logger.detachAppender(appender);
    logger.setLevel(ch.qos.logback.classic.Level.WARN);
  }

  private String output(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
  }

  @Test
  void createSuccessAuditsWithoutSensitiveFields() {
    ListAppender<ILoggingEvent> appender = attach();
    try {
      var request = new CreateApiKeyRequest("probe-key", false, 100L, null, List.of("gpt-4o"), List.of("1.1.1.1"));
      service.create(SESSION, request);

      String log = output(appender);
      assertThat(log).contains("event=apikey_security").contains("op=create")
          .contains("outcome=success").contains("requestId=req_");
      assertThat(log).doesNotContain("probe-key", "gpt-4o", "1.1.1.1", "upstream-session", "sk-");
    } finally {
      detach(appender);
    }
  }

  @Test
  void failureAuditsCarryOutcomeAndReasonWithoutSecret() {
    doThrow(new PortalException(PortalErrorCode.NOT_FOUND)).when(client).deleteToken(any(), anyLong());
    ListAppender<ILoggingEvent> appender = attach();
    try {
      assertThatThrownBy(() -> service.delete(SESSION, 999L)).isInstanceOf(PortalException.class);
      String log = output(appender);
      assertThat(log).contains("op=delete").contains("outcome=failure").contains("NOT_FOUND")
          .contains("resource=999");
      assertThat(log).doesNotContain("upstream-session");
    } finally {
      detach(appender);
    }
  }

  @Test
  void revealExceptionsNeverContainSecret() {
    when(client.revealToken(any(), anyLong())).thenReturn(SensitiveSecret.of("sk-abc"));
    ApiKeyRevealResponse response = service.reveal(SESSION, 7L);
    assertThat(response.secret()).isEqualTo("sk-abc");

    doThrow(new PortalException(PortalErrorCode.NOT_FOUND)).when(client).revealToken(any(), anyLong());
    assertThatThrownBy(() -> service.reveal(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> !String.valueOf(e.getMessage()).contains("sk-"));
  }

  @Test
  void subjectIsHashedNotRawUserId() {
    ListAppender<ILoggingEvent> appender = attach();
    try {
      service.delete(SESSION, 7L);
      String log = output(appender);
      assertThat(log).contains("subject=");
      assertThat(log).doesNotContain("subject=42");
    } finally {
      detach(appender);
    }
  }
}
