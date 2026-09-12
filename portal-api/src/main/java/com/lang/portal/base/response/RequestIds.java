package com.lang.portal.base.response;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestIds {
  public static final String HEADER = "X-Request-Id";
  public static final String ATTRIBUTE = "lang.requestId";

  private RequestIds() {}

  public static boolean isValid(String value, int minLength, int maxLength) {
    if (value == null || value.length() < minLength || value.length() > maxLength) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean ok =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.' || c == '_' || c == ':' || c == '-' ;
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  public static String generate() {
    return "req_" + UUID.randomUUID().toString().replace("-", "");
  }

  public static String current(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE);
    if (value instanceof String s && !s.isBlank()) {
      return s;
    }
    return "";
  }
}
