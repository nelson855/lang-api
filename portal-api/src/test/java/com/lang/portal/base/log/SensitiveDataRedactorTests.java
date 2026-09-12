package com.lang.portal.base.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTests {

  @Test
  void redactsCredentialsCaseInsensitively() {
    String input = "{\"Password\":\"secret123\",\"AUTHORIZATION\":\"Bearer abc\",\"api_key\":\"sk-full-key-123\",\"sign\":\"pay123\"}";
    String redacted = SensitiveDataRedactor.redact(input);
    assertThat(redacted).doesNotContain("secret123").doesNotContain("Bearer abc").doesNotContain("sk-full-key-123");
    assertThat(redacted).contains("[REDACTED]");
  }

  @Test
  void redactsPrivateAddresses() {
    String redacted = SensitiveDataRedactor.redact("connect to 192.168.1.10 failed");
    assertThat(redacted).doesNotContain("192.168.1.10");
  }
}
