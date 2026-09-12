package com.lang.portal.base.web;

import com.lang.portal.base.response.RequestIds;
import com.lang.portal.config.PortalCommonProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private final PortalCommonProperties properties;

  public RequestIdFilter(PortalCommonProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/portal/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    int min = properties.request().requestId().minLength();
    int max = properties.request().requestId().maxLength();
    String incoming = request.getHeader(RequestIds.HEADER);
    String requestId =
        RequestIds.isValid(incoming, min, max) ? incoming : RequestIds.generate();
    request.setAttribute(RequestIds.ATTRIBUTE, requestId);
    MDC.put("requestId", requestId);
    response.setHeader(RequestIds.HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
    }
  }
}
