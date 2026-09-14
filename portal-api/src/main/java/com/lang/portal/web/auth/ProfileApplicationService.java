package com.lang.portal.web.auth;

import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.profile.NewApiProfileUpdateClient;
import com.lang.portal.upstream.newapi.profile.ProfileUpdateCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ProfileApplicationService {

  private final AuthenticationRateLimiter rateLimiter;
  private final NewApiProfileUpdateClient profileUpdateClient;

  public ProfileApplicationService(
      AuthenticationRateLimiter rateLimiter, NewApiProfileUpdateClient profileUpdateClient) {
    this.rateLimiter = rateLimiter;
    this.profileUpdateClient = profileUpdateClient;
  }

  public AuthProfile update(
      PortalAuthenticatedUser user,
      NewApiSession session,
      ProfileUpdateRequest request,
      HttpServletRequest httpRequest) {
    rateLimiter.checkProfileUpdate(user.id(), httpRequest);
    NewApiUserProfile updated =
        profileUpdateClient.update(
            session,
            new ProfileUpdateCommand(
                request.username(),
                request.displayName(),
                request.currentPassword(),
                request.newPassword()));
    return new AuthProfile(updated.id(), updated.username(), updated.displayName(), updated.email());
  }
}
