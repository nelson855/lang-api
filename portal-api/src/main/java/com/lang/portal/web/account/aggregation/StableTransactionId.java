package com.lang.portal.web.account.aggregation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class StableTransactionId {

  private StableTransactionId() {}

  public static String consumption(long userId, String requestId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("用户 ID 必须为正数");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId 不能为空");
    }
    return "CONSUMPTION_" + sha256Hex("CONSUMPTION\0" + userId + "\0" + requestId);
  }

  private static String sha256Hex(String input) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("当前 JDK 不提供 SHA-256", e);
    }
    byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(hash.length * 2);
    for (byte b : hash) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}
