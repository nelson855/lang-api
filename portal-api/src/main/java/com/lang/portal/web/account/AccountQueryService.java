package com.lang.portal.web.account;

import com.lang.portal.base.money.QuotaMoneyConverter;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.balance.NewApiBalanceClient;
import org.springframework.stereotype.Component;

@Component
public class AccountQueryService {

  private final NewApiBalanceClient balanceClient;
  private final PortalCommonProperties properties;

  public AccountQueryService(NewApiBalanceClient balanceClient, PortalCommonProperties properties) {
    this.balanceClient = balanceClient;
    this.properties = properties;
  }

  public AccountBalanceDto balance(NewApiSession session) {
    long quota = balanceClient.currentQuota(session);
    long quotaPerUsd = properties.catalog().quotaPerUsd();
    return new AccountBalanceDto(
        Long.toString(quota), QuotaMoneyConverter.toUsd(quota, quotaPerUsd), "USD");
  }
}
