package com.lang.portal.upstream.newapi.topup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiTopupPageRaw(
    int page,
    @JsonProperty("page_size") int pageSize,
    long total,
    List<NewApiTopupEntryRaw> items) {
  public NewApiTopupPageRaw {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
