package com.lang.portal.web.auth;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
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
  private final PortalCommonProperties properties;

  public ProfileController(
      ProfileApplicationService applicationService, PortalCommonProperties properties) {
    this.applicationService = applicationService;
    this.properties = properties;
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
    NewApiSession session = session(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, applicationService.update(user, session, update, request)));
  }

  private NewApiSession session(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    Map<String, String> values = new HashMap<>();
    for (Cookie cookie : cookies) {
      String name = cookie.getName();
      if (!properties.auth().cookie().sessionName().equals(name)
          && !properties.auth().cookie().userIdName().equals(name)) {
        continue;
      }
      if (values.putIfAbsent(name, cookie.getValue()) != null) {
        throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
      }
    }
    String session = values.get(properties.auth().cookie().sessionName());
    String userId = values.get(properties.auth().cookie().userIdName());
    if (session == null || session.isBlank() || userId == null || userId.isBlank()) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    try {
      long id = Long.parseLong(userId);
      if (id > 0) {
        return new NewApiSession(session, id);
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
  }
}
