package com.lang.portal.upstream.newapi.token;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NewApiTokenSearch {
  private NewApiTokenSearch() {}

  public static String searchPath(String keyword, int page, int pageSize) {
    if (page < 1) {
      throw new IllegalArgumentException("page 必须从 1 开始");
    }
    if (pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("pageSize 必须在 1～100 之间");
    }
    String raw = keyword == null ? "" : keyword.trim();
    String encoded = URLEncoder.encode("%" + raw + "%", StandardCharsets.UTF_8).replace("+", "%20");
    return "/api/token/search?keyword=" + encoded + "&page=" + page + "&page_size=" + pageSize;
  }
}
