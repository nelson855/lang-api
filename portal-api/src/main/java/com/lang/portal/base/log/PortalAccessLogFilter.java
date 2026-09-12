package com.lang.portal.base.log;

import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PortalAccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(PortalAccessLogFilter.class);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/portal/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      chain.doFilter(request, response);
    } finally {
      String pattern =
          (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route = pattern != null ? pattern : "<unmatched-portal-route>";
      log.info("event=portal_access time={} requestId={} method={} route={} status={} durationMs={}",
          Instant.now().toString(),
          RequestIds.current(request),
          request.getMethod(),
          route,
          response.getStatus(),
          System.currentTimeMillis() - start);
    }
  }
}
