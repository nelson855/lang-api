package com.lang.portal.upstream.newapi.log;

import java.util.List;

public record NewApiLogPage(int total, List<NewApiLogRecord> items) {
  public NewApiLogPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
