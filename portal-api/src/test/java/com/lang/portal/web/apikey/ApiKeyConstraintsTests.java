package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyConstraintsTests {

  private static final Instant NOW = Instant.ofEpochSecond(1790000000L);

  private static PortalErrorCode codeOf(Runnable action) {
    try {
      action.run();
    } catch (PortalException e) {
      return e.errorCode();
    }
    throw new AssertionError("应当抛出 PortalException");
  }

  @Test
  void nameTrimsAndBoundsLength() {
    assertThat(ApiKeyConstraints.name("  probe  ")).isEqualTo("probe");
    assertThat(codeOf(() -> ApiKeyConstraints.name("   "))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.name("x".repeat(51)))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(ApiKeyConstraints.name("x".repeat(50))).hasSize(50);
  }

  @Test
  void quotaRejectsNegativeUnsafeAndContradictory() {
    assertThat(ApiKeyConstraints.remaining(false, 100L)).isEqualTo(100L);
    assertThat(ApiKeyConstraints.remaining(true, null)).isEqualTo(0L);
    assertThat(codeOf(() -> ApiKeyConstraints.remaining(false, -1L))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.remaining(false, 1L << 53))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.remaining(false, null))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.remaining(true, 100L))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void expiresMustBeStrictlyFuture() {
    assertThat(ApiKeyConstraints.expiresAt(null, NOW)).isNull();
    assertThat(ApiKeyConstraints.expiresAt(NOW.plusSeconds(60), NOW)).isEqualTo(NOW.plusSeconds(60));
    assertThat(codeOf(() -> ApiKeyConstraints.expiresAt(NOW, NOW))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.expiresAt(NOW.minusSeconds(1), NOW))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void modelsTrimDropEmptyDedupeAndRejectSeparators() {
    assertThat(ApiKeyConstraints.models(List.of(" gpt-4o ", "", "gpt-4o", "claude-sonnet")))
        .containsExactly("gpt-4o", "claude-sonnet");
    assertThat(ApiKeyConstraints.models(null)).isEmpty();
    assertThat(codeOf(() -> ApiKeyConstraints.models(List.of("a,b")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.models(List.of("a\u0001b")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void ipsAcceptLiteralsAndNormalize() {
    assertThat(ApiKeyConstraints.ips(List.of("1.1.1.1", "::1")))
        .containsExactly("1.1.1.1", "0:0:0:0:0:0:0:1");
    assertThat(ApiKeyConstraints.ips(List.of("  8.8.8.8  "))).containsExactly("8.8.8.8");
    assertThat(ApiKeyConstraints.ips(null)).isEmpty();
  }

  @Test
  void ipsRejectCidrDomainPortAndInjection() {
    assertThat(codeOf(() -> ApiKeyConstraints.ips(List.of("1.1.1.0/24")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.ips(List.of("example.com")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.ips(List.of("1.1.1.1:443")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.ips(List.of("1.1.1.1\n2.2.2.2")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(codeOf(() -> ApiKeyConstraints.ips(List.of("999.1.1.1")))).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
    assertThat(ApiKeyConstraints.ips(List.of("1.1.1.1"))).containsExactly("1.1.1.1");
  }

  @Test
  void errorMessagesNamePublicFieldWithoutEchoingBody() {
    try {
      ApiKeyConstraints.name("");
      throw new AssertionError("应当抛出");
    } catch (PortalException e) {
      assertThat(e.errorCode()).isEqualTo(PortalErrorCode.INVALID_ARGUMENT);
      assertThat(e.getMessage()).contains("name");
    }
  }
}
