package com.lang.portal.base.security;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/portal/api/test-protected", produces = MediaType.APPLICATION_JSON_VALUE)
public class TestProtectedController {

  @GetMapping
  @ProtectedEndpoint
  public ApiResponse<String> guarded(HttpServletRequest request) {
    return ApiResponses.ok(request, "guarded");
  }

  @GetMapping("/admin")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<String> admin(HttpServletRequest request) {
    return ApiResponses.ok(request, "admin");
  }
}
