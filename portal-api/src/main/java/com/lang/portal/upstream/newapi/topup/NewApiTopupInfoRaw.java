package com.lang.portal.upstream.newapi.topup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiTopupInfoRaw(
    @JsonProperty("enable_online_topup") Boolean online,
    @JsonProperty("enable_stripe_topup") Boolean stripe,
    @JsonProperty("enable_creem_topup") Boolean creem,
    @JsonProperty("enable_waffo_topup") Boolean waffo,
    @JsonProperty("enable_waffo_pancake_topup") Boolean waffoPancake) {}
