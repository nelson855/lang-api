package com.lang.portal.base.response;

import java.util.List;

public record PageData<T>(List<T> items, int page, int pageSize, long total) {
  public static <T> PageData<T> of(List<T> items, int page, int pageSize, long total) {
    if (page < 1) {
      throw new IllegalArgumentException("page 从 1 开始");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("pageSize 为正整数");
    }
    if (total < 0) {
      throw new IllegalArgumentException("total 非负");
    }
    return new PageData<>(List.copyOf(items), page, pageSize, total);
  }
}
