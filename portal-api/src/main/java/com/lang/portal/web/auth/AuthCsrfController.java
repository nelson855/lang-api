package com.lang.portal.web.auth;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/portal/api/auth/csrf")
public class AuthCsrfController {

  private final AuthCsrfService service;

  public AuthCsrfController(AuthCsrfService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<CsrfResponse>> csrf(HttpServletRequest request) {
    AuthCsrfService.IssuedCsrf issued = service.issue();
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Set-Cookie", issued.cookie().toString())
        .body(ApiResponses.ok(request, new CsrfResponse(issued.token())));
  }

  public record CsrfResponse(String token) {}
}
