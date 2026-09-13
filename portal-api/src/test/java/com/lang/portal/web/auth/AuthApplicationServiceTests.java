package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import org.junit.jupiter.api.Test;

class AuthApplicationServiceTests {

  @Test
  void disabledRegistrationRejectsBeforeCallingUpstream() {
    PortalCommonProperties properties = new PortalCommonProperties();
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    assertThatThrownBy(() -> new AuthApplicationService(properties, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.REGISTRATION_DISABLED);

    verifyNoInteractions(client);
  }

  @Test
  void enabledRegistrationOnlyCreatesUpstreamUser() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(true);
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    new AuthApplicationService(properties, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse"));

    verify(client).register(new NewApiCredentials("ordinary", "correct-horse"));
  }

  @Test
  void loginReturnsOnlyTheSessionAndMinimalProfileNeededByThePortal() {
    PortalCommonProperties properties = new PortalCommonProperties();
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    NewApiLoginResult upstream = new NewApiLoginResult(
        new NewApiSession("upstream-session", 42L),
        new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    when(client.login(new NewApiCredentials("ordinary", "correct-horse"))).thenReturn(upstream);

    AuthLoginResult result = new AuthApplicationService(properties, client)
        .login(new LoginRequest("ordinary", "correct-horse"));

    assertThat(result.session()).isEqualTo(upstream.session());
    assertThat(result.profile()).isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    verify(client).login(new NewApiCredentials("ordinary", "correct-horse"));
  }
}
