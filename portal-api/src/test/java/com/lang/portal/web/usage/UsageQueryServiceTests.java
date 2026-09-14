package com.lang.portal.web.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.usage.NewApiHourlyClient;
import com.lang.portal.upstream.newapi.usage.NewApiHourlyRow;
import com.lang.portal.upstream.newapi.usage.NewApiSummary;
import com.lang.portal.upstream.newapi.usage.NewApiSummaryClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageQueryServiceTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final NewApiSummaryClient summaryClient = mock(NewApiSummaryClient.class);
  private final NewApiHourlyClient hourlyClient = mock(NewApiHourlyClient.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private UsageQueryService service() {
    return new UsageQueryService(summaryClient, hourlyClient, properties);
  }

  @Test
  void summaryConvertsQuotaCentrally() {
    when(summaryClient.fetch(eq(SESSION), any())).thenReturn(new NewApiSummary(1200L, 30L, 4000L));

    UsageSummaryDto dto = service().summary(SESSION, null, null, FIXED);

    assertThat(dto.quota()).isEqualTo("1200");
    assertThat(dto.amount()).isEqualTo("0.0024");
    assertThat(dto.currency()).isEqualTo("USD");
    assertThat(dto.rateWindowSeconds()).isEqualTo(60);
  }

  @Test
  void timeseriesMergesAndSortsWithAmounts() {
    when(hourlyClient.fetch(eq(SESSION), any()))
        .thenReturn(
            List.of(
                new NewApiHourlyRow(1789034400L, "b", 3L, 50L, 200L),
                new NewApiHourlyRow(1789034400L, "a", 5L, 100L, 500L)));

    UsageTimeseriesDto dto = service()
        .timeseries(SESSION, "2026-09-10T09:00:00Z", "2026-09-10T11:00:00Z", FIXED);

    assertThat(dto.granularity()).isEqualTo("HOUR");
    assertThat(dto.points()).hasSize(1);
    assertThat(dto.points().get(0).requestCount()).isEqualTo(8L);
    assertThat(dto.points().get(0).amount()).isEqualTo("0.0014");
  }

  @Test
  void timeseriesEmptyStaysEmpty() {
    when(hourlyClient.fetch(eq(SESSION), any())).thenReturn(List.of());

    UsageTimeseriesDto dto = service()
        .timeseries(SESSION, "2026-09-10T09:00:00Z", "2026-09-10T11:00:00Z", FIXED);

    assertThat(dto.points()).isEmpty();
  }
}
