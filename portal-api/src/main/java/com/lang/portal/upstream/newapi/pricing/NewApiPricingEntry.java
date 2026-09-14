package com.lang.portal.upstream.newapi.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiPricingEntry(
    @JsonProperty("model_name") String modelName,
    @JsonProperty("vendor_id") Integer vendorId,
    @JsonProperty("quota_type") Integer quotaType,
    @JsonProperty("model_ratio") BigDecimal modelRatio,
    @JsonProperty("model_price") BigDecimal modelPrice,
    @JsonProperty("completion_ratio") BigDecimal completionRatio) {}
