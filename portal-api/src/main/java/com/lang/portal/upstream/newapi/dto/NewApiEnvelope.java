package com.lang.portal.upstream.newapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiEnvelope<T>(boolean success, String message, T data) {}
