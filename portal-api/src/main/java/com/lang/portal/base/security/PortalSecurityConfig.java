package com.lang.portal.base.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableMethodSecurity
public class PortalSecurityConfig {

  private final ObjectMapper mapper = new ObjectMapper();

  @Bean
  SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
    http.formLogin(form -> form.disable());
    http.httpBasic(basic -> basic.disable());
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/portal/api/public-config"));
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/portal/api/public-config").permitAll()
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
        .requestMatchers("/actuator/**").denyAll()
        .requestMatchers("/portal/api/**").permitAll()
        .anyRequest().permitAll());
    http.exceptionHandling(handling -> handling
        .authenticationEntryPoint(authenticationEntryPoint())
        .accessDeniedHandler(accessDeniedHandler()));
    return http.build();
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) -> writeError(request, response, 401, "UNAUTHENTICATED", "尚未登录或登录已过期");
  }

  private AccessDeniedHandler accessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      boolean anonymous =
          authentication == null
              || !authentication.isAuthenticated()
              || authentication.getAuthorities().stream()
                  .anyMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority()));
      if (anonymous) {
        writeError(request, response, 401, "UNAUTHENTICATED", "尚未登录或登录已过期");
      } else {
        writeError(request, response, 403, "FORBIDDEN", "没有访问权限");
      }
    };
  }

  private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
      throws java.io.IOException {
    Object requestIdAttr = request.getAttribute(RequestIds.ATTRIBUTE);
    String requestId =
        requestIdAttr instanceof String s && !s.isBlank() ? s : RequestIds.generate();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(RequestIds.HEADER, requestId);
    mapper.writeValue(response.getOutputStream(), ApiResponses.failure(requestId, code, message));
  }
}
