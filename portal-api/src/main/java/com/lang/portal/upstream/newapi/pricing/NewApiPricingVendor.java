package com.lang.portal.upstream.newapi.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiPricingVendor(
    @JsonProperty("id") Integer id,
    @JsonProperty("name") String name) {}
