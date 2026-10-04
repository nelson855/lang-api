package com.lang.portal.web.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.base.money.QuotaMoneyConverter;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 请求日志与 Dashboard、消费汇总共用 QuotaMoneyConverter：同一组原始 quota 必须得到同一份金额。
 * 这里只固定“同一权威换算路径”，不声称已验证真实扣费与 USD 等值。
 */
class RequestLogFeeConsistencyTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
  private static final long QUOTA_PER_USD = 500_000L;

  private final NewApiLogClient logClient = mock(NewApiLogClient.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private List<RequestLogDto> listWithQuota(long... quotas) {
    List<NewApiLogRecord> records = new ArrayList<>();
    for (int i = 0; i < quotas.length; i++) {
      records.add(
          new NewApiLogRecord(
              Instant.parse("2026-09-10T10:00:00Z").plusSeconds(i),
              "probe-key-01",
              "gpt-test",
              NewApiLogResult.SUCCESS,
              10L,
              20L,
              3000L,
              true,
              quotas[i],
              "req-" + i,
              9L));
    }
    when(logClient.listSuccess(eq(SESSION), any(NewApiLogQuery.class)))
        .thenReturn(new NewApiLogPage(records.size(), records));
    return new RequestLogQueryService(logClient, properties)
        .list(SESSION, 1, 20, "SUCCESS", null, null, null, null, FIXED)
        .items();
  }

  @Test
  void sameQuotaYieldsSameAmountAsTheSharedConverter() {
    List<RequestLogDto> items = listWithQuota(QUOTA_PER_USD, 250_000L, 0L, 1L);

    for (RequestLogDto dto : items) {
      assertThat(dto.amount()).isEqualTo(QuotaMoneyConverter.toUsd(Long.parseLong(dto.quota()), QUOTA_PER_USD));
      assertThat(dto.currency()).isEqualTo("USD");
      // 原始 quota 保持十进制字符串，不做浮点转换
      assertThat(dto.quota()).matches("\\d+");
    }
  }

  @Test
  void realZeroQuotaShowsZeroRatherThanMissing() {
    List<RequestLogDto> items = listWithQuota(0L);

    assertThat(items.get(0).quota()).isEqualTo("0");
    assertThat(items.get(0).amount()).isEqualTo("0.0");
  }

  @Test
  void minimalAmountKeepsFullPrecisionWithoutScientificNotation() {
    List<RequestLogDto> items = listWithQuota(1L);

    assertThat(items.get(0).amount()).isEqualTo("0.000002");
    assertThat(items.get(0).amount()).doesNotContain("e").doesNotContain("E");
  }

  @Test
  void largeQuotaStaysExactWithinLongRange() {
    List<RequestLogDto> items = listWithQuota(Long.MAX_VALUE - 1);

    assertThat(items.get(0).quota()).isEqualTo("9223372036854775806");
    assertThat(items.get(0).amount()).isNotBlank();
  }

  @Test
  void summingRoundedRowsMayDifferFromRoundingTheSumButBothStayDecimal() {
    List<RequestLogDto> items = listWithQuota(3_333_333L, 3_333_333L, 3_333_333L);

    BigDecimal rowSum = BigDecimal.ZERO;
    BigDecimal quotaSum = BigDecimal.ZERO;
    for (RequestLogDto dto : items) {
      rowSum = rowSum.add(new BigDecimal(dto.amount()));
      quotaSum = quotaSum.add(new BigDecimal(dto.quota()));
    }
    BigDecimal totalRounded =
        QuotaMoneyConverter.toUsd(quotaSum.longValueExact(), QUOTA_PER_USD) == null
            ? null
            : new BigDecimal(QuotaMoneyConverter.toUsd(quotaSum.longValueExact(), QUOTA_PER_USD));

    assertThat(totalRounded).isNotNull();
    // 允许逐条与合计的舍入差异，但两者都是十进制金额，不会出现科学计数法
    assertThat(rowSum.toPlainString()).doesNotContain("E");
    assertThat(totalRounded.toPlainString()).doesNotContain("E");
  }
}