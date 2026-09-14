package com.lang.portal.web.account;

import com.lang.portal.base.money.QuotaMoneyConverter;
import com.lang.portal.base.response.PageData;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.balance.NewApiBalanceClient;
import com.lang.portal.upstream.newapi.topup.NewApiTopupInfoClient;
import com.lang.portal.upstream.newapi.topup.NewApiTopupRecordsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccountQueryService {

  private final NewApiBalanceClient balanceClient;
  private final NewApiTopupInfoClient topupInfoClient;
  private final NewApiTopupRecordsClient topupRecordsClient;
  private final PortalCommonProperties properties;

  public AccountQueryService(NewApiBalanceClient balanceClient, PortalCommonProperties properties) {
    this(balanceClient, null, null, properties);
  }

  @Autowired
  public AccountQueryService(
      NewApiBalanceClient balanceClient,
      NewApiTopupInfoClient topupInfoClient,
      NewApiTopupRecordsClient topupRecordsClient,
      PortalCommonProperties properties) {
    this.balanceClient = balanceClient;
    this.topupInfoClient = topupInfoClient;
    this.topupRecordsClient = topupRecordsClient;
    this.properties = properties;
  }

  public AccountBalanceDto balance(NewApiSession session) {
    long quota = balanceClient.currentQuota(session);
    long quotaPerUsd = properties.catalog().quotaPerUsd();
    return new AccountBalanceDto(
        Long.toString(quota), QuotaMoneyConverter.toUsd(quota, quotaPerUsd), "USD");
  }

  public TopupCapability topupOptions(NewApiSession session) {
    return topupInfoClient.capability(session);
  }

  public PageData<TopupRecord> topups(NewApiSession session, int page, int pageSize) {
    return topupRecordsClient.page(session, page, pageSize);
  }
}
