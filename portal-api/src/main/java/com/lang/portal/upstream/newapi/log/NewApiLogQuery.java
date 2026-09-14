package com.lang.portal.upstream.newapi.log;

import com.lang.portal.web.usage.UsageTimeRange;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record NewApiLogQuery(
    int page, int pageSize, String keyName, String model, long startTimestamp, long endTimestamp) {

  public static NewApiLogQuery of(
      int page, int pageSize, String keyName, String model, long startTimestamp, long endTimestamp) {
    if (page < 1) {
      throw new IllegalArgumentException("page 必须从 1 开始");
    }
    if (pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("pageSize 必须在 1～100 之间");
    }
    String key = keyName == null || keyName.isBlank() ? null : keyName.trim();
    String mdl = model == null || model.isBlank() ? null : model.trim();
    if (key != null && key.length() > 64) {
      throw new IllegalArgumentException("Key 筛选过长");
    }
    if (mdl != null && mdl.length() > 128) {
      throw new IllegalArgumentException("模型筛选过长");
    }
    if (startTimestamp < 0 || endTimestamp < 0 || startTimestamp > endTimestamp) {
      throw new IllegalArgumentException("时间边界非法");
    }
    return new NewApiLogQuery(page, pageSize, key, mdl, startTimestamp, endTimestamp);
  }

  public static NewApiLogQuery fromRange(
      int page, int pageSize, String keyName, String model, UsageTimeRange range) {
    if (range == null) {
      throw new IllegalArgumentException("时间范围不能为空");
    }
    return of(
        page,
        pageSize,
        keyName,
        model,
        range.upstreamStartTimestamp(),
        range.upstreamEndTimestamp());
  }

  public String toPath(NewApiLogResult result) {
    if (result == null) {
      throw new IllegalArgumentException("结果类型不能为空");
    }
    StringBuilder path = new StringBuilder("/api/log/self?");
    path.append("page=").append(page).append("&page_size=").append(pageSize);
    path.append("&type=").append(result.upstreamType());
    if (keyName != null) {
      path.append("&token_name=").append(encode(keyName));
    }
    if (model != null) {
      path.append("&model_name=").append(encode(likeContains(model)));
    }
    path.append("&start_timestamp=").append(startTimestamp);
    path.append("&end_timestamp=").append(endTimestamp);
    return path.toString();
  }

  static String likeContains(String raw) {
    StringBuilder escaped = new StringBuilder("%");
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '!' || c == '%' || c == '_') {
        escaped.append('!');
      }
      escaped.append(c);
    }
    escaped.append('%');
    return escaped.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
