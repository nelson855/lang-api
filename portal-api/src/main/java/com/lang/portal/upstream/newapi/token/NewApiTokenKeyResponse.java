package com.lang.portal.upstream.newapi.token;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record NewApiTokenKeyResponse(@JsonProperty("key") String key) {}
