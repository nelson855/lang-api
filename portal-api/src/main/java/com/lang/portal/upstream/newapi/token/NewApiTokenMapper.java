package com.lang.portal.upstream.newapi.token;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class NewApiTokenMapper {
  private NewApiTokenMapper() {}

  public static String toStatus(
      Integer upstreamStatus, Long expiredTimeSec, Long remainQuota, Boolean unlimitedQuota, long nowSec) {
    if (expiredTimeSec != null && expiredTimeSec != -1L && expiredTimeSec <= nowSec) {
      return "expired";
    }
    if (!Boolean.TRUE.equals(unlimitedQuota) && remainQuota != null && remainQuota <= 0) {
      return "exhausted";
    }
    if (upstreamStatus != null && upstreamStatus == 1) {
      return "enabled";
    }
    if (upstreamStatus != null && upstreamStatus == 2) {
      return "disabled";
    }
    throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
  }

  public static Instant toExpiresAt(Long expiredTimeSec) {
    if (expiredTimeSec == null || expiredTimeSec == -1L) {
      return null;
    }
    return Instant.ofEpochSecond(expiredTimeSec);
  }

  public static List<String> splitModels(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String part : raw.split(",", -1)) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return List.copyOf(result);
  }

  public static String joinModels(List<String> models) {
    if (models == null || models.isEmpty()) {
      return "";
    }
    return String.join(",", models);
  }

  public static List<String> splitIps(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String part : raw.split("\n", -1)) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return List.copyOf(result);
  }

  public static String joinIps(List<String> ips) {
    if (ips == null || ips.isEmpty()) {
      return "";
    }
    return String.join("\n", ips);
  }

  public static String normalizeSecret(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    String stripped = raw.trim();
    while (stripped.startsWith("sk-")) {
      stripped = stripped.substring("sk-".length());
    }
    if (stripped.isEmpty()) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return "sk-" + stripped;
  }

  public static String normalizeMask(String mask) {
    if (mask == null || mask.isBlank()) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    String stripped = mask.trim();
    while (stripped.startsWith("sk-")) {
      stripped = stripped.substring("sk-".length());
    }
    return "sk-" + stripped;
  }
}
