package com.lang.portal.upstream.newapi.policy;

import com.lang.portal.config.PortalCommonProperties;
import java.time.Duration;
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
    if (setCookies == null) {
      return List.of();
    }
    for (String header : setCookies) {
      String pair = header.split(";", 2)[0];
      int equals = pair.indexOf('=');
      if (equals <= 0 || !"session".equals(pair.substring(0, equals).trim())) {
        continue;
      }
      String session = pair.substring(equals + 1);
      if (session.isBlank()) {
        continue;
      }
      return List.of(sessionCookie(session));
    }
    return List.of();
  }

  public List<ResponseCookie> createSessionCookies(String session, long userId) {
    return List.of(sessionCookie(session), userIdCookie(userId));
  }

  public List<ResponseCookie> expireSessionCookies() {
    return List.of(expire(properties.auth().cookie().sessionName()), expire(properties.auth().cookie().userIdName()));
  }

  private ResponseCookie sessionCookie(String session) {
    return create(properties.auth().cookie().sessionName(), session).build();
  }

  private ResponseCookie userIdCookie(long userId) {
    return create(properties.auth().cookie().userIdName(), Long.toString(userId)).build();
  }

  private ResponseCookie.ResponseCookieBuilder create(String name, String value) {
    return ResponseCookie.from(name, value)
        .path(properties.auth().cookie().path())
        .httpOnly(true)
        .sameSite(properties.auth().cookie().sameSite())
        .secure(properties.auth().cookie().secure())
        .maxAge(properties.auth().cookie().maxAge());
  }

  private ResponseCookie expire(String name) {
    return ResponseCookie.from(name, "")
        .path(properties.auth().cookie().path())
        .httpOnly(true)
        .sameSite(properties.auth().cookie().sameSite())
        .secure(properties.auth().cookie().secure())
        .maxAge(Duration.ZERO)
        .build();
  }
}
