package com.lang.portal.web.account;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public final class TopupRecordMapper {

  private static final Set<String> KNOWN_METHODS = Set.of("ALIPAY", "WXPAY", "STRIPE", "CREEM", "WAFFO");
  private static final int MAX_ORDER_ID_LENGTH = 128;

  private TopupRecordMapper() {}

  public static TopupRecord from(
      String tradeNo, String amount, String paymentMethod, String status, long createTime, long completeTime) {
    String orderId = orderId(tradeNo);
    String requestedAmount = amount(amount);
    String method = method(paymentMethod);
    String mappedStatus = status(status);
    String createdAt = instant(createTime, "create_time");
    String completedAt = completedAt(mappedStatus, completeTime);
    return new TopupRecord(orderId, requestedAmount, "USD", method, mappedStatus, createdAt, completedAt);
  }

  private static String orderId(String raw) {
    if (raw == null || raw.isBlank() || raw.trim().length() > MAX_ORDER_ID_LENGTH) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return raw.trim();
  }

  private static String amount(String raw) {
    try {
      BigDecimal value = new BigDecimal(String.valueOf(raw).trim());
      if (value.signum() < 0) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      return value.toPlainString();
    } catch (UpstreamException e) {
      throw e;
    } catch (Exception e) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
  }

  private static String method(String raw) {
    if (raw == null || raw.isBlank()) {
      return "OTHER";
    }
    String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
    if (KNOWN_METHODS.contains(normalized)) {
      return normalized;
    }
    return "OTHER";
  }

  private static String status(String raw) {
    if (raw == null) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "pending" -> "PENDING";
      case "success" -> "SUCCEEDED";
      case "failed" -> "FAILED";
      case "expired" -> "EXPIRED";
      default -> throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    };
  }

  private static String instant(long epochSeconds, String field) {
    if (epochSeconds <= 0) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    try {
      return Instant.ofEpochSecond(epochSeconds).toString();
    } catch (Exception e) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
  }

  private static String completedAt(String mappedStatus, long completeTime) {
    if ("PENDING".equals(mappedStatus)) {
      if (completeTime != 0) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      return null;
    }
    return instant(completeTime, "complete_time");
  }
}
