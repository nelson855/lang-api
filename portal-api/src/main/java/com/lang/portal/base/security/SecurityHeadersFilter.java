package com.lang.portal.base.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private final Environment environment;

  public SecurityHeadersFilter(Environment environment) {
    this.environment = environment;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    String uri = request.getRequestURI();
    if (!uri.startsWith("/portal/api/") && !uri.startsWith("/actuator/")) {
      response.setHeader("Content-Security-Policy",
          "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    }
    if (environment.acceptsProfiles(Profiles.of("prod")) && request.isSecure()) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
    chain.doFilter(request, response);
  }
}
