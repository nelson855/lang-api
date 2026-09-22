package com.lang.portal.upstream.newapi.log;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiLogEntryRaw(
    @JsonProperty("created_time") Long createdTime,
    @JsonProperty("type") Integer type,
    @JsonProperty("token_name") String tokenName,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("quota") Long quota,
    @JsonProperty("prompt_tokens") Long promptTokens,
    @JsonProperty("completion_tokens") Long completionTokens,
    @JsonProperty("use_time") Long useTime,
    @JsonProperty("is_stream") Boolean stream,
    @JsonProperty("request_id") String requestId,
    @JsonProperty("token_id") Long tokenId) {}
