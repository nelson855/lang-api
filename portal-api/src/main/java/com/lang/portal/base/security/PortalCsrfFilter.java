package com.lang.portal.base.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import com.lang.portal.web.auth.AuthCsrfService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PortalCsrfFilter extends OncePerRequestFilter {

  private static final Set<String> MUTATION_PATHS = Set.of(
      "/portal/api/auth/register",
      "/portal/api/auth/login",
      "/portal/api/auth/refresh",
      "/portal/api/auth/logout");

  private final AuthCsrfService csrfService;
  private final ObjectMapper objectMapper;

  public PortalCsrfFilter(AuthCsrfService csrfService, ObjectMapper objectMapper) {
    this.csrfService = csrfService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod()) || !MUTATION_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      csrfService.requireValid(request);
    } catch (PortalException exception) {
      String requestId = RequestIds.current(request);
      if (requestId.isBlank()) {
        requestId = RequestIds.generate();
      }
      response.setStatus(exception.errorCode().status().value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader(RequestIds.HEADER, requestId);
      objectMapper.writeValue(
          response.getOutputStream(),
          ApiResponses.failure(requestId, exception.errorCode().name(), exception.errorCode().message()));
      return;
    }
    chain.doFilter(request, response);
  }
}
