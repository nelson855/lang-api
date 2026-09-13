package com.lang.portal.web.auth;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/portal/api/profile")
public class ProfileController {

  @GetMapping
  public ResponseEntity<ApiResponse<AuthProfile>> profile(
      @AuthenticationPrincipal PortalAuthenticatedUser user, HttpServletRequest request) {
    AuthProfile profile = new AuthProfile(user.id(), user.username(), user.displayName(), user.email());
    return ResponseEntity.ok(ApiResponses.ok(request, profile));
  }
}
