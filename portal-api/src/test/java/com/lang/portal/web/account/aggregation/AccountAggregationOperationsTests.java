package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountAggregationOperationsTests {

  @Test
  void whitelistsFixedOperationNames() {
    assertThat(AccountAggregationOperations.isAllowed("account-consumption-snapshot")).isTrue();
    assertThat(AccountAggregationOperations.isAllowed("account-consumption-summary")).isTrue();
    assertThat(AccountAggregationOperations.isAllowed("account-transactions")).isTrue();
  }

  @Test
  void rejectsUnknownOperations() {
    assertThat(AccountAggregationOperations.isAllowed("dashboard-stats")).isFalse();
    assertThat(AccountAggregationOperations.isAllowed("user-42")).isFalse();
    assertThat(AccountAggregationOperations.isAllowed("")).isFalse();
    assertThat(AccountAggregationOperations.isAllowed(null)).isFalse();
  }

  @Test
  void requireAllowedThrowsOnUnknown() {
    assertThatThrownBy(() -> AccountAggregationOperations.requireAllowed("user-42"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(AccountAggregationOperations.requireAllowed("account-transactions"))
        .isEqualTo("account-transactions");
  }

  @Test
  void whitelistDoesNotCarryHighCardinalityLabels() {
    // 确认操作名本身不包含用户 ID、时间戳、类型筛选等高基数值
    for (String operation : new String[]{
        AccountAggregationOperations.SNAPSHOT,
        AccountAggregationOperations.SUMMARY,
        AccountAggregationOperations.TRANSACTIONS}) {
      assertThat(operation).doesNotContain("user");
      assertThat(operation).doesNotContain("model");
      assertThat(operation).doesNotContain("requestId");
      assertThat(operation).doesNotContain("type");
      assertThat(operation).doesNotContain("page");
    }
  }
}
