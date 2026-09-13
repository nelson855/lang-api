package com.lang.portal.upstream.newapi.token;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NewApiTokenPage(
    int page,
    @JsonProperty("page_size") int pageSize,
    int total,
    List<NewApiToken> items) {
  public NewApiTokenPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
