package com.lang.portal.web.account;

import java.util.List;

public record TopupCapability(boolean enabled, List<String> methods, String currency, String reason) {

  public static TopupCapability fromUpstreamFlags(
      boolean online, boolean stripe, boolean creem, boolean waffo, boolean waffoPancake) {
    boolean anyEnabled = online || stripe || creem || waffo || waffoPancake;
    String reason = anyEnabled ? "UNSUPPORTED_PROVIDER" : "NOT_CONFIGURED";
    return new TopupCapability(false, List.of(), "USD", reason);
  }
}
