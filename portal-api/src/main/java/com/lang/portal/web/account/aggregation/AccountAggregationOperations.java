package com.lang.portal.web.account.aggregation;

import java.util.Set;

public final class AccountAggregationOperations {

  public static final String SNAPSHOT = "account-consumption-snapshot";
  public static final String SUMMARY = "account-consumption-summary";
  public static final String TRANSACTIONS = "account-transactions";

  private static final Set<String> ALLOWED = Set.of(SNAPSHOT, SUMMARY, TRANSACTIONS);

  private AccountAggregationOperations() {}

  public static boolean isAllowed(String operation) {
    return operation != null && ALLOWED.contains(operation);
  }

  public static String requireAllowed(String operation) {
    if (!isAllowed(operation)) {
      throw new IllegalArgumentException("未在白名单内的账户聚合操作 " + operation);
    }
    return operation;
  }
}
