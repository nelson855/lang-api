package com.lang.portal.upstream.newapi.token;

import java.util.List;

public record NewApiUpdateTokenCommand(
    String name,
    Boolean unlimited,
    Long remainQuota,
    Long expiredTime,
    List<String> models,
    List<String> ips) {
  public NewApiUpdateTokenCommand {
    models = models == null ? null : List.copyOf(models);
    ips = ips == null ? null : List.copyOf(ips);
  }
}
