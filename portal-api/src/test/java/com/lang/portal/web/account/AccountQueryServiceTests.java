package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.balance.NewApiBalanceClient;
import org.junit.jupiter.api.Test;

class AccountQueryServiceTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  @Test
  void balanceConvertsQuotaWithSharedRate() {
    NewApiBalanceClient balanceClient = mock(NewApiBalanceClient.class);
    when(balanceClient.currentQuota(eq(SESSION))).thenReturn(500000L);
    AccountQueryService service = new AccountQueryService(balanceClient, new PortalCommonProperties());

    AccountBalanceDto dto = service.balance(SESSION);

    assertThat(dto.quota()).isEqualTo("500000");
    assertThat(dto.amount()).isEqualTo("1.0");
    assertThat(dto.currency()).isEqualTo("USD");
  }

  @Test
  void zeroBalanceStaysZero() {
    NewApiBalanceClient balanceClient = mock(NewApiBalanceClient.class);
    when(balanceClient.currentQuota(eq(SESSION))).thenReturn(0L);
    AccountQueryService service = new AccountQueryService(balanceClient, new PortalCommonProperties());

    AccountBalanceDto dto = service.balance(SESSION);

    assertThat(dto.quota()).isEqualTo("0");
    assertThat(dto.amount()).isEqualTo("0.0");
  }
}
