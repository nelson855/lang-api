package com.lang.portal.web.auth;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.base.security.PortalUpstreamSessions;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/portal/api/profile")
public class ProfileController {

  private final ProfileApplicationService applicationService;

  public ProfileController(ProfileApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<AuthProfile>> profile(
      @AuthenticationPrincipal PortalAuthenticatedUser user, HttpServletRequest request) {
    AuthProfile profile = new AuthProfile(user.id(), user.username(), user.displayName(), user.email());
    return ResponseEntity.ok(ApiResponses.ok(request, profile));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<AuthProfile>> update(
      @RequestBody Map<String, String> body,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    ProfileUpdateRequest update = ProfileUpdateRequest.resolve(body);
    NewApiSession session = PortalUpstreamSessions.require(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, applicationService.update(user, session, update, request)));
  }
}