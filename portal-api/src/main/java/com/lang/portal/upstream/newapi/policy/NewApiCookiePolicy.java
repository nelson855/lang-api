package com.lang.portal.upstream.newapi.policy;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class NewApiCookiePolicy {

  private final PortalCommonProperties properties;

  public NewApiCookiePolicy(PortalCommonProperties properties) {
    this.properties = properties;
  }

  public List<ResponseCookie> convert(List<String> setCookies) {
    List<ResponseCookie> result = new ArrayList<>();
    if (setCookies == null) {
      return result;
    }
    for (String header : setCookies) {
      String[] parts = header.split(";", -1);
      if (parts.length == 0) {
        continue;
      }
      String[] nameValue = parts[0].split("=", 2);
      if (nameValue.length != 2 || nameValue[0].isBlank()) {
        continue;
      }
      ResponseCookie.ResponseCookieBuilder builder =
          ResponseCookie.from(nameValue[0].trim(), nameValue[1].trim())
              .path(properties.portal().cookie().path())
              .httpOnly(true)
              .sameSite(properties.portal().cookie().sameSite())
              .secure(properties.portal().cookie().secure());
      result.add(builder.build());
    }
    return result;
  }

  public ResponseCookie expire(String name) {
    return ResponseCookie.from(name, "")
        .path(properties.portal().cookie().path())
        .httpOnly(true)
        .sameSite(properties.portal().cookie().sameSite())
        .secure(properties.portal().cookie().secure())
        .maxAge(Duration.ZERO)
        .build();
  }
}
