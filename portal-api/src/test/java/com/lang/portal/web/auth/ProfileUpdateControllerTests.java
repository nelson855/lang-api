package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProfileUpdateControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary User", "ordinary@example.test");
  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private final ProfileApplicationService applicationService = mock(ProfileApplicationService.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private ProfileController controller() {
    return new ProfileController(applicationService, properties);
  }

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "upstream-session"), new Cookie("LANG_UID", "42"));
    return request;
  }

  private Map<String, String> validBody() {
    Map<String, String> body = new HashMap<>();
    body.put("username", "newname");
    body.put("displayName", "New Name");
    body.put("currentPassword", "correct-123");
    return body;
  }

  @Test
  void updateReturnsLatestTrimmedProfile() {
    AuthProfile latest = new AuthProfile(42L, "newname", "New Name", "ordinary@example.test");
    when(applicationService.update(eq(USER), eq(SESSION), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(latest);

    var response = controller().update(validBody(), USER, validRequest());

    assertThat(response.getBody().data().username()).isEqualTo("newname");
    assertThat(response.getBody().data().displayName()).isEqualTo("New Name");
    assertThat(response.getBody().data().id()).isEqualTo(42L);
  }

  @Test
  void updateRejectsUnsupportedFieldBeforeService() {
    Map<String, String> body = validBody();
    body.put("email", "evil@example.test");

    assertThatThrownBy(() -> controller().update(body, USER, validRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(applicationService);
  }

  @Test
  void anonymousUpdateIsUnauthenticated() {
    assertThatThrownBy(() -> controller().update(validBody(), USER, new MockHttpServletRequest()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(applicationService);
  }

  @Test
  void rateLimitedUpdatePropagates() {
    when(applicationService.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PortalException(PortalErrorCode.RATE_LIMITED));

    assertThatThrownBy(() -> controller().update(validBody(), USER, validRequest()))
        .matches(e -> e instanceof PortalException
            && ((PortalException) e).errorCode() == PortalErrorCode.RATE_LIMITED);
  }

  @Test
  void wrongCurrentPasswordPropagatesAsInvalidArgument() {
    when(applicationService.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PortalException(PortalErrorCode.INVALID_ARGUMENT));

    assertThatThrownBy(() -> controller().update(validBody(), USER, validRequest()))
        .matches(e -> e instanceof PortalException
            && ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void usernameConflictPropagates() {
    when(applicationService.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PortalException(PortalErrorCode.RESOURCE_CONFLICT));

    assertThatThrownBy(() -> controller().update(validBody(), USER, validRequest()))
        .matches(e -> e instanceof PortalException
            && ((PortalException) e).errorCode() == PortalErrorCode.RESOURCE_CONFLICT);
  }

  @Test
  void unknownResultPropagates() {
    when(applicationService.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN));

    assertThatThrownBy(() -> controller().update(validBody(), USER, validRequest()))
        .matches(e -> e instanceof PortalException
            && ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }
}
