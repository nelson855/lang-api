package com.lang.portal.web.apikey;

import com.lang.portal.base.response.RequestIds;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ApiKeySecurityEvents {

  private static final Logger log = LoggerFactory.getLogger(ApiKeySecurityEvents.class);
  private static final byte[] SALT = new byte[16];

  static {
    new SecureRandom().nextBytes(SALT);
  }

  public void completed(
      String op, NewApiSession session, String resourceId, String outcome, String reason) {
    log.info("event=apikey_security op={} subject={} resource={} outcome={} reason={} requestId={}",
        op,
        subject(session),
        resourceId == null ? "" : resourceId,
        outcome,
        reason,
        requestId());
  }

  private String subject(NewApiSession session) {
    if (session == null) {
      return "unknown";
    }
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      sha.update(SALT);
      byte[] digest =
          sha.digest(Long.toString(session.userId()).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private String requestId() {
    try {
      var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        String id = RequestIds.current(request);
        if (!id.isBlank()) {
          return id;
        }
      }
    } catch (Exception ignored) {
    }
    return RequestIds.generate();
  }
}
