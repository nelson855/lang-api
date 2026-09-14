package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.profile.NewApiProfileUpdateClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProfileApplicationServiceTests {

  private final AuthenticationRateLimiter rateLimiter = mock(AuthenticationRateLimiter.class);
  private final NewApiProfileUpdateClient profileClient = mock(NewApiProfileUpdateClient.class);

  private ProfileApplicationService service() {
    return new ProfileApplicationService(rateLimiter, profileClient);
  }

  @Test
  void rateLimitsBeforeUpstreamAndReturnsWhitelistedProfile() {
    PortalAuthenticatedUser user =
        new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
    NewApiSession session = new NewApiSession("upstream-session", 42L);
    ProfileUpdateRequest request = ProfileUpdateRequest.resolve(Map.of(
        "username", "newname",
        "displayName", "New Name",
        "currentPassword", "correct-123"));
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    when(profileClient.update(eq(session), any()))
        .thenReturn(new NewApiUserProfile(42L, "newname", "New Name", "ordinary@example.test"));

    AuthProfile result = service().update(user, session, request, httpRequest);

    inOrder(rateLimiter, profileClient)
        .verify(rateLimiter)
        .checkProfileUpdate(eq(42L), eq(httpRequest));
    assertThat(result).isEqualTo(new AuthProfile(42L, "newname", "New Name", "ordinary@example.test"));
  }

  @Test
  void rateLimitedSkipsUpstream() {
    PortalAuthenticatedUser user =
        new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
    NewApiSession session = new NewApiSession("upstream-session", 42L);
    ProfileUpdateRequest request = ProfileUpdateRequest.resolve(Map.of(
        "username", "newname",
        "displayName", "New Name",
        "currentPassword", "correct-123"));
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    org.mockito.Mockito.doThrow(new PortalException(PortalErrorCode.RATE_LIMITED))
        .when(rateLimiter)
        .checkProfileUpdate(eq(42L), eq(httpRequest));

    assertThatThrownBy(() -> service().update(user, session, request, httpRequest))
        .matches(e -> e instanceof PortalException
            && ((PortalException) e).errorCode() == PortalErrorCode.RATE_LIMITED);
    verifyNoInteractions(profileClient);
  }
}
