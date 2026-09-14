package com.lang.portal.web.auth;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.util.Map;
import java.util.Set;

public record ProfileUpdateRequest(
    String username, String displayName, String currentPassword, String newPassword) {

  private static final Set<String> ALLOWED =
      Set.of("username", "displayName", "currentPassword", "newPassword", "confirmPassword");

  public static ProfileUpdateRequest resolve(Map<String, String> params) {
    Map<String, String> safe = params == null ? Map.of() : params;
    for (String key : safe.keySet()) {
      if (!ALLOWED.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
    String username = username(safe.get("username"));
    String displayName = displayName(safe.get("displayName"));
    String currentPassword = password(safe.get("currentPassword"), "currentPassword", false);
    String newPassword = pairedNewPassword(safe.get("newPassword"), safe.get("confirmPassword"));
    return new ProfileUpdateRequest(username, displayName, currentPassword, newPassword);
  }

  private static String username(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.length() < 3 || value.length() > 32 || hasControl(value)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 username 不合法");
    }
    return value;
  }

  private static String displayName(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    if (value.length() > 64 || hasControl(value)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 displayName 不合法");
    }
    return value;
  }

  private static String password(String raw, String field, boolean allowBlank) {
    if (raw == null || raw.isEmpty()) {
      if (allowBlank) {
        return null;
      }
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + field + " 不合法");
    }
    if (raw.isBlank() || raw.length() > 128 || hasControl(raw)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + field + " 不合法");
    }
    return raw;
  }

  private static String pairedNewPassword(String rawNew, String rawConfirm) {
    boolean hasNew = rawNew != null && !rawNew.isEmpty();
    boolean hasConfirm = rawConfirm != null && !rawConfirm.isEmpty();
    if (!hasNew && !hasConfirm) {
      return null;
    }
    if (!hasNew || !hasConfirm || !rawNew.equals(rawConfirm)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 newPassword 不合法");
    }
    String value = password(rawNew, "newPassword", false);
    if (value.length() < 8) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 newPassword 不合法");
    }
    return value;
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

  @Override
  public String toString() {
    return "ProfileUpdateRequest[username=***, displayName=***, currentPassword=***, newPassword=***]";
  }
}
