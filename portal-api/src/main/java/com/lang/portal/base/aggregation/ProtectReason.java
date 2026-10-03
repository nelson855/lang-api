package com.lang.portal.base.aggregation;

public enum ProtectReason {
  RANGE("range"),
  PAGES("pages"),
  RECORDS("records"),
  SINGLE_TIMEOUT("single-timeout"),
  DEADLINE("deadline"),
  INCONSISTENT_PAGE("inconsistent-page");

  private final String tag;

  ProtectReason(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
