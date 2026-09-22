package com.lang.portal.infrastructure.aggregation;

public enum ProtectReason {
  RANGE("range"),
  PAGES("pages"),
  RECORDS("records"),
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
