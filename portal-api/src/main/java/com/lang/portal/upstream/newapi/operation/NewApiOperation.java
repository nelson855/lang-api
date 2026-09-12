package com.lang.portal.upstream.newapi.operation;

import org.springframework.http.HttpMethod;

public record NewApiOperation(String name, HttpMethod method, String path, boolean authenticated) {
  public NewApiOperation {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("操作名不能为空");
    }
    if (method == null) {
      throw new IllegalArgumentException("方法不能为空");
    }
    if (path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("相对路径必须以 / 开头");
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
      throw new IllegalArgumentException("不接受绝对 URL");
    }
  }
}
