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
@RequestMapping("/portal/api/auth/options")
public class AuthOptionsController {

  private final AuthOptionsService service;

  public AuthOptionsController(AuthOptionsService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<AuthOptions>> options(HttpServletRequest request) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(ApiResponses.ok(request, service.getOptions()));
  }

}
