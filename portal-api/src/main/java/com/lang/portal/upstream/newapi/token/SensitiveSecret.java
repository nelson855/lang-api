package com.lang.portal.upstream.newapi.token;

import java.util.Arrays;

public final class SensitiveSecret implements AutoCloseable {
  private char[] value;
  private boolean cleared;

  private SensitiveSecret(char[] value) {
    this.value = value;
  }

  public static SensitiveSecret of(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("敏感值不能为空");
    }
    return new SensitiveSecret(raw.toCharArray());
  }

  public synchronized String asString() {
    if (cleared || value == null) {
      throw new IllegalStateException("敏感值已清除");
    }
    return new String(value);
  }

  public synchronized void clear() {
    if (value != null) {
      Arrays.fill(value, '\0');
      value = null;
    }
    cleared = true;
  }

  @Override
  public void close() {
    clear();
  }

  @Override
  public String toString() {
    return "SensitiveSecret[REDACTED]";
  }
}
