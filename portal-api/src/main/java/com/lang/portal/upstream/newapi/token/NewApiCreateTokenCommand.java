package com.lang.portal.upstream.newapi.token;

import java.util.List;

public record NewApiCreateTokenCommand(
    String name,
    boolean unlimited,
    long remainQuota,
    Long expiredTime,
    List<String> models,
    List<String> ips) {
  public NewApiCreateTokenCommand {
    models = models == null ? List.of() : List.copyOf(models);
    ips = ips == null ? List.of() : List.copyOf(ips);
  }
}
