package com.lang.portal.upstream.newapi.token;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiToken(
    Long id,
    @JsonProperty("user_id") Long userId,
    String key,
    Integer status,
    String name,
    @JsonProperty("created_time") Long createdTime,
    @JsonProperty("accessed_time") Long accessedTime,
    @JsonProperty("expired_time") Long expiredTime,
    @JsonProperty("remain_quota") Long remainQuota,
    @JsonProperty("unlimited_quota") Boolean unlimitedQuota,
    @JsonProperty("model_limits_enabled") Boolean modelLimitsEnabled,
    @JsonProperty("model_limits") String modelLimits,
    @JsonProperty("allow_ips") String allowIps,
    @JsonProperty("used_quota") Long usedQuota,
    String group,
    @JsonProperty("cross_group_retry") Boolean crossGroupRetry) {}
