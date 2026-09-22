package com.lang.portal.infrastructure.aggregation;

public enum CacheOutcome {
  HIT("hit"),
  MISS("miss"),
  COALESCED("coalesced");

  private final String tag;

  CacheOutcome(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
