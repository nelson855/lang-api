package com.lang.portal.upstream.newapi.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record NewApiUserResponse(
    Long id,
    String username,
    @JsonProperty("display_name") String displayName,
    String email,
    Integer status) {}
