package com.lang.portal.web.account;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.util.Map;
import java.util.Set;

public record TopupPageQuery(int page, int pageSize) {

  private static final Set<String> ALLOWED_PARAMS = Set.of("page", "pageSize");

  public static TopupPageQuery resolve(Map<String, String> params, int defaultPageSize, int maxPageSize) {
    Map<String, String> safeParams = params == null ? Map.of() : params;
    for (String key : safeParams.keySet()) {
      if (!ALLOWED_PARAMS.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
    return new TopupPageQuery(parsePage(safeParams.get("page")), parsePageSize(safeParams.get("pageSize"), defaultPageSize, maxPageSize));
  }

  private static int parsePage(String raw) {
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value >= 1) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 page 不合法");
  }

  private static int parsePageSize(String raw, int defaultPageSize, int maxPageSize) {
    if (raw == null || raw.isBlank()) {
      return defaultPageSize;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value >= 1 && value <= maxPageSize) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 pageSize 不合法");
  }
}
