package com.lang.portal.base.log;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataRedactor {
  private static final List<String> KEYS =
      List.of("password", "passwd", "pwd", "cookie", "set-cookie", "authorization",
          "access_token", "access-token", "accesstoken", "api_key", "api-key", "apikey",
          "sign", "signature", "pay_sign", "token");

  private static final Pattern PRIVATE_IP =
      Pattern.compile("\\b(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3})\\b");

  private SensitiveDataRedactor() {}

  public static String redact(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    String result = input;
    for (String key : KEYS) {
      Pattern jsonField = Pattern.compile(
          "(\"" + Pattern.quote(key) + "\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE);
      result = jsonField.matcher(result).replaceAll("$1[REDACTED]$3");
      Pattern kv = Pattern.compile(
          "(\\b" + Pattern.quote(key) + "\\b\\s*[:=]\\s*)([^\\s,;\"']+)", Pattern.CASE_INSENSITIVE);
      result = kv.matcher(result).replaceAll("$1[REDACTED]");
    }
    Matcher ip = PRIVATE_IP.matcher(result);
    result = ip.replaceAll("[REDACTED]");
    return result;
  }
}
