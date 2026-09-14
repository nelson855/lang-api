package com.lang.portal.upstream.newapi.usage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiHourlyRowRaw(
    @JsonProperty("hour") Long hour,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("request_count") Long requestCount,
    @JsonProperty("token_count") Long tokenCount,
    @JsonProperty("quota") Long quota) {}
