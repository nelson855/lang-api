package com.lang.portal.web.auth;

import com.lang.portal.config.PortalCommonProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class PortalClientAddressResolver {

  private final PortalCommonProperties properties;

  public PortalClientAddressResolver(PortalCommonProperties properties) {
    this.properties = properties;
  }

  public String resolve(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    if (!isTrusted(remoteAddress)) {
      return remoteAddress;
    }
    String forwarded = request.getHeader(properties.auth().trustedProxy().forwardedForHeader());
    if (forwarded == null || forwarded.isBlank()) {
      return remoteAddress;
    }
    String candidate = forwarded.split(",", 2)[0].trim();
    return isIpAddress(candidate) ? candidate : remoteAddress;
  }

  private boolean isTrusted(String address) {
    return Arrays.stream(properties.auth().trustedProxy().cidrs().split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .anyMatch(cidr -> contains(cidr, address));
  }

  private boolean contains(String cidr, String address) {
    try {
      String[] parts = cidr.split("/", 2);
      InetAddress network = InetAddress.getByName(parts[0]);
      InetAddress candidate = InetAddress.getByName(address);
      if (!network.getClass().equals(candidate.getClass())) {
        return false;
      }
      int prefix = parts.length == 1 ? network.getAddress().length * 8 : Integer.parseInt(parts[1]);
      byte[] networkBytes = network.getAddress();
      byte[] candidateBytes = candidate.getAddress();
      if (prefix < 0 || prefix > networkBytes.length * 8) {
        return false;
      }
      for (int index = 0; index < networkBytes.length; index++) {
        int remaining = prefix - index * 8;
        if (remaining <= 0) {
          return true;
        }
        int mask = remaining >= 8 ? 0xff : 0xff << (8 - remaining);
        if ((networkBytes[index] & mask) != (candidateBytes[index] & mask)) {
          return false;
        }
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isIpAddress(String value) {
    if (!value.matches("(?:\\d{1,3}\\.){3}\\d{1,3}") && !value.contains(":")) {
      return false;
    }
    try {
      InetAddress parsed = InetAddress.getByName(value);
      return value.equals(parsed.getHostAddress()) || value.contains(":");
    } catch (Exception ignored) {
      return false;
    }
  }
}
