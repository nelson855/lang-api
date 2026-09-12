package com.lang.portal.base.response;

import jakarta.servlet.http.HttpServletRequest;

public final class ApiResponses {
  private ApiResponses() {}

  public static <T> ApiResponse<T> ok(String requestId, T data) {
    return new ApiResponse<>(requestId, data, null);
  }

  public static <T> ApiResponse<T> ok(HttpServletRequest request, T data) {
    return ok(RequestIds.current(request), data);
  }

  public static <T> ApiResponse<T> failure(String requestId, String code, String message) {
    return new ApiResponse<>(requestId, null, new ApiError(code, message));
  }
}
