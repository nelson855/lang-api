package com.lang.portal.upstream.newapi.policy;

import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;

public final class NewApiHeaderPolicy {
  private NewApiHeaderPolicy() {}

  public static Map<String, String> requestHeaders(NewApiOperation operation, String requestId, Map<String, String> auth) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", MediaType.APPLICATION_JSON_VALUE);
    headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    if (requestId != null && !requestId.isBlank()) {
      headers.put("X-Request-Id", requestId);
    }
    if (operation.authenticated() && auth != null) {
      String cookie = auth.get("Cookie");
      if (cookie != null && !cookie.isBlank()) {
        headers.put("Cookie", cookie);
      }
      String user = auth.get("New-Api-User");
      if (user != null && !user.isBlank()) {
        headers.put("New-Api-User", user);
      }
    }
    return headers;
  }
}
