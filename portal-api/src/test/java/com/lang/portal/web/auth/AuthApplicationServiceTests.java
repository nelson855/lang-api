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
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.web.legal.LegalContentDocument;
import com.lang.portal.web.legal.LegalContentService;
import com.lang.portal.web.legal.LegalContentType;
import org.junit.jupiter.api.Test;

class AuthApplicationServiceTests {

  @Test
  void disabledRegistrationRejectsBeforeCallingUpstream() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    assertThatThrownBy(() -> service(false, PublicationMode.PUBLIC, true, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.REGISTRATION_DISABLED);

    verifyNoInteractions(client);
  }

  @Test
  void previewModeRejectsRegistrationAtSubmitTimeBeforeCallingUpstream() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    assertThatThrownBy(() -> service(true, PublicationMode.PREVIEW, true, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.REGISTRATION_DISABLED);

    verifyNoInteractions(client);
  }

  @Test
  void withdrawnLegalContentRejectsRegistrationAtSubmitTimeBeforeCallingUpstream() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    assertThatThrownBy(() -> service(true, PublicationMode.PUBLIC, false, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.REGISTRATION_DISABLED);

    verifyNoInteractions(client);
  }

  @Test
  void enabledRegistrationOnlyCreatesUpstreamUser() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);

    service(true, PublicationMode.PUBLIC, true, client)
        .register(new RegisterRequest("ordinary", "correct-horse", "correct-horse"));

    verify(client).register(new NewApiCredentials("ordinary", "correct-horse"));
  }

  @Test
  void loginReturnsOnlyTheSessionAndMinimalProfileNeededByThePortal() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    NewApiLoginResult upstream = new NewApiLoginResult(
        new NewApiSession("upstream-session", 42L),
        new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    when(client.login(new NewApiCredentials("ordinary", "correct-horse"))).thenReturn(upstream);

    AuthLoginResult result = service(true, PublicationMode.PUBLIC, true, client)
        .login(new LoginRequest("ordinary", "correct-horse"));

    assertThat(result.session()).isEqualTo(upstream.session());
    assertThat(result.profile()).isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    verify(client).login(new NewApiCredentials("ordinary", "correct-horse"));
  }

  private static AuthApplicationService service(
      boolean adminEnabled, PublicationMode mode, boolean legalAvailable, NewApiAuthenticationClient client) {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(adminEnabled);
    PublicationProperties publication = new PublicationProperties();
    publication.setMode(mode);
    LegalContentService legal = mock(LegalContentService.class);
    if (legalAvailable) {
      when(legal.document(LegalContentType.TERMS))
          .thenReturn(new LegalContentDocument(LegalContentType.TERMS, "用户协议", "<p>safe</p>", "zh-CN"));
      when(legal.document(LegalContentType.PRIVACY))
          .thenReturn(new LegalContentDocument(LegalContentType.PRIVACY, "隐私政策", "<p>safe</p>", "zh-CN"));
    } else {
      when(legal.document(LegalContentType.TERMS))
          .thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));
    }
    return new AuthApplicationService(new RegistrationPolicyService(properties, publication, legal), client);
  }
}
