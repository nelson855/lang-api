package com.lang.portal.infrastructure.aggregation;

public enum AggregationSource {
  SUCCESS_LOG("success-log"),
  ERROR_LOG("error-log");

  private final String tag;

  AggregationSource(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
