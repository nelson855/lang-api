package com.lang.portal.infrastructure.aggregation;

public enum AggregationOutcome {
  SUCCESS("success"),
  FAILURE("failure");

  private final String tag;

  AggregationOutcome(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
