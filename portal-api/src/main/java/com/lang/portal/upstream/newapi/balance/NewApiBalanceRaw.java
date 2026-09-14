package com.lang.portal.upstream.newapi.balance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiBalanceRaw(Long quota) {}
