package com.lang.portal.web.apikey;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ApiKeyConstraints {
  private static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

  private ApiKeyConstraints() {}

  public static String name(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty() || value.length() > 50) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 name 不合法");
    }
    return value;
  }

  public static long remaining(boolean unlimited, Long remaining) {
    if (unlimited) {
      if (remaining != null) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 quota 不合法");
      }
      return 0L;
    }
    if (remaining == null || remaining < 0 || remaining > MAX_SAFE_INTEGER) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 quota 不合法");
    }
    return remaining;
  }

  public static Instant expiresAt(Instant value, Instant now) {
    if (value == null) {
      return null;
    }
    Instant base = now == null ? Instant.now() : now;
    if (!value.isAfter(base)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 expiresAt 不合法");
    }
    return value;
  }

  public static List<String> models(List<String> raw) {
    if (raw == null) {
      return List.of();
    }
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String item : raw) {
      String value = item == null ? "" : item.trim();
      if (value.isEmpty()) {
        continue;
      }
      if (value.contains(",") || hasControl(value)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 modelRestrictions 不合法");
      }
      result.add(value);
    }
    return List.copyOf(result);
  }

  public static List<String> ips(List<String> raw) {
    if (raw == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String item : raw) {
      String value = item == null ? "" : item.trim();
      if (value.isEmpty()) {
        continue;
      }
      result.add(ipLiteral(value));
    }
    return List.copyOf(result);
  }

  private static String ipLiteral(String value) {
    if (hasControl(value)
        || value.contains("/")
        || value.contains(" ")
        || value.contains("\t")
        || value.contains(",")
        || value.contains(";")
        || value.contains("%")) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 allowedIps 不合法");
    }
    try {
      if (isIPv4Shape(value)) {
        String normalized = InetAddress.getByName(value).getHostAddress();
        if (!isIPv4Shape(normalized)) {
          throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 allowedIps 不合法");
        }
        return normalized;
      }
      if (value.contains(":")) {
        if (value.contains(".")) {
          String head = value.substring(0, value.indexOf('.'));
          if (!head.contains(":")) {
            throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 allowedIps 不合法");
          }
        }
        return InetAddress.getByName(value).getHostAddress();
      }
    } catch (PortalException e) {
      throw e;
    } catch (Exception e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 allowedIps 不合法");
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 allowedIps 不合法");
  }

  private static boolean isIPv4Shape(String value) {
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4) {
      return false;
    }
    for (String part : parts) {
      if (part.isEmpty() || part.length() > 3) {
        return false;
      }
      for (int i = 0; i < part.length(); i++) {
        if (!Character.isDigit(part.charAt(i))) {
          return false;
        }
      }
      try {
        if (Integer.parseInt(part) > 255) {
          return false;
        }
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasControl(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        return true;
      }
    }
    return false;
  }
}
