package com.lang.portal.upstream.newapi.usage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiSummaryRaw(Long quota, Long rpm, Long tpm) {}
