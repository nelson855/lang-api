package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StableTransactionIdTests {

  @Test
  void consumptionIdIsDeterministic() {
    String a = StableTransactionId.consumption(42L, "req-abc");
    String b = StableTransactionId.consumption(42L, "req-abc");
    assertThat(a).isEqualTo(b);
    assertThat(a).startsWith("CONSUMPTION_");
    // 64 hex chars of SHA-256
    assertThat(a.substring("CONSUMPTION_".length())).matches("[0-9a-f]{64}");
  }

  @Test
  void differentUsersProduceDifferentIdsForSameRequestId() {
    String a = StableTransactionId.consumption(1L, "shared-req");
    String b = StableTransactionId.consumption(2L, "shared-req");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void differentRequestIdsProduceDifferentIds() {
    String a = StableTransactionId.consumption(1L, "r1");
    String b = StableTransactionId.consumption(1L, "r2");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void rejectsBlankRequestId() {
    assertThatThrownBy(() -> StableTransactionId.consumption(1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StableTransactionId.consumption(1L, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidUserId() {
    assertThatThrownBy(() -> StableTransactionId.consumption(0L, "r"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StableTransactionId.consumption(-1L, "r"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
