package com.lang.portal.upstream.newapi.probe;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AggregationSensitiveScanner {

  public enum ViolationKind {
    COOKIE_HEADER,
    AUTHORIZATION_BEARER,
    RAW_API_KEY,
    EMAIL,
    PHONE,
    IP_ADDRESS,
    REQUEST_CONTENT,
    INTERNAL_CHANNEL,
    SUPPLIER_SECRET,
    SESSION_TOKEN
  }

  public record Violation(ViolationKind kind, String path, String snippet) {}

  public record ScanResult(List<Violation> violations) {
    public boolean clean() {
      return violations.isEmpty();
    }
  }

  private static final Pattern COOKIE_HEADER =
      Pattern.compile("(?i)\\bcookie\\s*:");
  private static final Pattern SET_COOKIE_HEADER =
      Pattern.compile("(?i)\\bset-cookie\\s*:");
  private static final Pattern AUTHORIZATION_BEARER =
      Pattern.compile("(?i)\\b(authorization\\s*:\\s*)?bearer\\s+[A-Za-z0-9._\\-]{8,}");
  private static final Pattern RAW_API_KEY =
      Pattern.compile("\\bsk-[A-Za-z0-9]{20,}\\b");
  private static final Pattern EMAIL =
      Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
  private static final Pattern PHONE =
      Pattern.compile("\\b1[3-9]\\d{9}\\b");
  private static final Pattern IPV4_STANDALONE =
      Pattern.compile("(?<![\\d.])\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?![\\d.])");
  private static final Pattern SUPPLIER_AKIA = Pattern.compile("\\bAKIA[A-Z0-9]{16}\\b");
  private static final Pattern LONG_HEX_TOKEN = Pattern.compile("\\b[a-f0-9]{32,}\\b");

  private static final List<String> SUSPICIOUS_FIELD_NAMES =
      List.of(
          "content",
          "prompt",
          "messages",
          "request_body",
          "request_content",
          "session",
          "session_key",
          "access_token",
          "auth_token",
          "api_key",
          "secret",
          "password",
          "private_key");

  private static final List<String> SECRET_FIELD_NAMES =
      List.of(
          "supplier_secret",
          "vendor_secret",
          "upstream_secret",
          "channel_secret",
          "api_secret");

  private static final List<String> CHANNEL_FIELD_NAMES = List.of("channel", "channel_id");

  public ScanResult scan(JsonNode root) {
    List<Violation> violations = new ArrayList<>();
    walk(root, "$", violations);
    return new ScanResult(List.copyOf(violations));
  }

  public void scanOrThrow(JsonNode root) {
    ScanResult result = scan(root);
    if (!result.clean()) {
      StringBuilder sb = new StringBuilder("sensitive content detected in fixture:");
      for (Violation v : result.violations()) {
        sb.append(" [").append(v.kind()).append("] at ").append(v.path);
      }
      throw new AggregationProbeException(sb.toString());
    }
  }

  private void walk(JsonNode node, String path, List<Violation> out) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              entry -> {
                String fieldPath = path + "." + entry.getKey();
                inspectField(entry.getKey(), entry.getValue(), fieldPath, out);
                walk(entry.getValue(), fieldPath, out);
              });
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), path + "[" + i + "]", out);
      }
    } else if (node.isTextual()) {
      inspectValue(node.asText(), path, out);
    }
  }

  private void inspectField(String fieldName, JsonNode value, String path, List<Violation> out) {
    String lower = fieldName.toLowerCase();
    if (value != null && value.isTextual()) {
      String text = value.asText();
      if (SECRET_FIELD_NAMES.contains(lower)) {
        out.add(new Violation(ViolationKind.SUPPLIER_SECRET, path, truncate(text)));
        return;
      }
      if (CHANNEL_FIELD_NAMES.contains(lower) && !text.startsWith("<")) {
        if (!text.matches("\\d+") || text.length() > 3) {
          out.add(new Violation(ViolationKind.INTERNAL_CHANNEL, path, truncate(text)));
          return;
        }
      }
      if (SUSPICIOUS_FIELD_NAMES.contains(lower) && !text.startsWith("<")) {
        out.add(new Violation(ViolationKind.REQUEST_CONTENT, path, truncate(text)));
        return;
      }
      if (("session".equals(lower) || "session_key".equals(lower) || "access_token".equals(lower))
          && !text.startsWith("<")
          && text.length() >= 24) {
        out.add(new Violation(ViolationKind.SESSION_TOKEN, path, truncate(text)));
        return;
      }
      if (("trade_no".equals(lower) || "transaction_id".equals(lower))
          && !text.startsWith("<")
          && text.length() >= 8) {
        if (!text.matches(".*[A-Z_].*")) {
          out.add(new Violation(ViolationKind.SESSION_TOKEN, path, truncate(text)));
        }
      }
    }
  }

  private void inspectValue(String text, String path, List<Violation> out) {
    if (text == null || text.isEmpty()) {
      return;
    }
    if (text.startsWith("<") && text.endsWith(">")) {
      return;
    }
    if (SET_COOKIE_HEADER.matcher(text).find() || COOKIE_HEADER.matcher(text).find()) {
      out.add(new Violation(ViolationKind.COOKIE_HEADER, path, truncate(text)));
    }
    if (AUTHORIZATION_BEARER.matcher(text).find()) {
      out.add(new Violation(ViolationKind.AUTHORIZATION_BEARER, path, truncate(text)));
    }
    if (RAW_API_KEY.matcher(text).find()) {
      out.add(new Violation(ViolationKind.RAW_API_KEY, path, truncate(text)));
    }
    if (EMAIL.matcher(text).find()) {
      out.add(new Violation(ViolationKind.EMAIL, path, truncate(text)));
    }
    if (PHONE.matcher(text).find()) {
      out.add(new Violation(ViolationKind.PHONE, path, truncate(text)));
    }
    if (IPV4_STANDALONE.matcher(text).find()
        && !isVersionOrNumericLiteral(text)
        && isNotLoopbackIp(text)) {
      out.add(new Violation(ViolationKind.IP_ADDRESS, path, truncate(text)));
    }
    if (SUPPLIER_AKIA.matcher(text).find()) {
      out.add(new Violation(ViolationKind.SUPPLIER_SECRET, path, truncate(text)));
    }
    if (LONG_HEX_TOKEN.matcher(text).find()
        && ("$.data.session".equals(path) || path.endsWith(".session"))) {
      out.add(new Violation(ViolationKind.SESSION_TOKEN, path, truncate(text)));
    }
  }

  private static boolean looksLikeVersionOrNumber(String text) {
    if (text == null) {
      return true;
    }
    String trimmed = text.trim();
    if (trimmed.matches("^\\d+(\\.\\d+)+$")) {
      return true;
    }
    if (trimmed.contains("version") || trimmed.contains("v0.") || trimmed.contains("v1.")) {
      return true;
    }
    return false;
  }

  private static boolean isVersionOrNumericLiteral(String text) {
    if (text == null) {
      return true;
    }
    String trimmed = text.trim();
    if (!trimmed.matches("^\\d+(\\.\\d+)+$")) {
      return false;
    }
    String[] parts = trimmed.split("\\.");
    for (String part : parts) {
      int n;
      try {
        n = Integer.parseInt(part);
      } catch (NumberFormatException e) {
        return false;
      }
      if (n > 255) {
        return false;
      }
    }
    if (parts.length != 4) {
      return true;
    }
    return false;
  }

  private static boolean isNotLoopbackIp(String text) {
    if (text == null) {
      return true;
    }
    String trimmed = text.trim();
    return !trimmed.contains("127.0.0.1") && !trimmed.contains("0.0.0.0");
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 40 ? s : s.substring(0, 40) + "...";
  }
}
