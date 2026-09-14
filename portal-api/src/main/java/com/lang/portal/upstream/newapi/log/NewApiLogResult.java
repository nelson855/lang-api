package com.lang.portal.upstream.newapi.log;

public enum NewApiLogResult {
  SUCCESS(2),
  ERROR(5);

  private final int upstreamType;

  NewApiLogResult(int upstreamType) {
    this.upstreamType = upstreamType;
  }

  public int upstreamType() {
    return upstreamType;
  }
}
