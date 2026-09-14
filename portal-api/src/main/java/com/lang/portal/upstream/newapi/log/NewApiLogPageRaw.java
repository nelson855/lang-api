package com.lang.portal.upstream.newapi.log;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiLogPageRaw(
    int page,
    @JsonProperty("page_size") int pageSize,
    int total,
    List<NewApiLogEntryRaw> items) {
  public NewApiLogPageRaw {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
