package com.lang.portal.upstream.newapi.token;

import com.fasterxml.jackson.annotation.JsonProperty;

record NewApiCreateTokenRequest(
    String name,
    @JsonProperty("remain_quota") long remainQuota,
    @JsonProperty("unlimited_quota") boolean unlimitedQuota,
    @JsonProperty("expired_time") long expiredTime,
    @JsonProperty("model_limits_enabled") boolean modelLimitsEnabled,
    @JsonProperty("model_limits") String modelLimits,
    @JsonProperty("allow_ips") String allowIps) {}
