package com.lang.portal.upstream.newapi.topup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiTopupEntryRaw(
    @JsonProperty("trade_no") String tradeNo,
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("payment_method") String paymentMethod,
    @JsonProperty("create_time") Long createTime,
    @JsonProperty("complete_time") Long completeTime,
    @JsonProperty("status") String status) {}
