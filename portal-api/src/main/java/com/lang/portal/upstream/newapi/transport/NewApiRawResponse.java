package com.lang.portal.upstream.newapi.transport;

import java.util.List;

public record NewApiRawResponse<T>(
    int status, boolean success, T data, List<String> setCookies, String message) {}
