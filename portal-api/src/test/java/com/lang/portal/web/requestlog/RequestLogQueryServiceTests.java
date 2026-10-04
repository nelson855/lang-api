package com.lang.portal.web.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestLogQueryServiceTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final NewApiLogClient logClient = mock(NewApiLogClient.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private RequestLogQueryService service() {
    return new RequestLogQueryService(logClient, properties);
  }

  @Test
  void mapsRecordsToStableDtoWithUsd() {
    NewApiLogRecord record = new NewApiLogRecord(
        Instant.parse("2026-09-10T10:00:00Z"),
        "probe-key-01",
        "gpt-test",
        NewApiLogResult.SUCCESS,
        10L,
        20L,
        3000L,
        true,
        500000L,
        "req-1",
        9L);
    when(logClient.listSuccess(eq(SESSION), any(NewApiLogQuery.class)))
        .thenReturn(new NewApiLogPage(1, List.of(record)));

    var page = service().list(SESSION, 1, 20, "SUCCESS", null, null, null, null, FIXED);

    assertThat(page.total()).isEqualTo(1);
    RequestLogDto dto = page.items().get(0);
    assertThat(dto.result()).isEqualTo("SUCCESS");
    assertThat(dto.keyName()).isEqualTo("probe-key-01");
    assertThat(dto.quota()).isEqualTo("500000");
    assertThat(dto.amount()).isEqualTo("1.0");
    assertThat(dto.currency()).isEqualTo("USD");
    assertThat(dto.protocol()).isNull();
    assertThat(dto.firstTokenLatencyMs()).isNull();
  }

  @Test
  void errorResultDelegatesToErrorOperation() {
    when(logClient.listError(eq(SESSION), any(NewApiLogQuery.class)))
        .thenReturn(new NewApiLogPage(0, List.of()));

    var page = service().list(SESSION, 1, 20, "ERROR", null, null, null, null, FIXED);

    assertThat(page.total()).isZero();
  }
}
