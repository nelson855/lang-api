package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.infrastructure.session.PortalSessionRecord;
import com.lang.portal.infrastructure.session.PortalSessionStore;
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
  void loginIssuesAServerSideSessionAndExposesOnlyTheOpaqueIdentifier() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    PortalSessionStore sessionStore = mock(PortalSessionStore.class);
    NewApiLoginResult upstream = new NewApiLoginResult(
        new NewApiSession("upstream-session", 42L),
        new NewApiUserProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    when(client.login(new NewApiCredentials("ordinary", "correct-horse"))).thenReturn(upstream);
    when(sessionStore.create("upstream-session", 42L)).thenReturn("server-side-session-id");

    AuthLoginResult result = service(true, PublicationMode.PUBLIC, true, client, sessionStore)
        .login(new LoginRequest("ordinary", "correct-horse"));

    assertThat(result.session()).isEqualTo(new PortalSessionHandle("server-side-session-id", 42L));
    // 浏览器拿到的标识不能暴露上游会话值。
    assertThat(result.session().sessionId()).doesNotContain("upstream-session");
    assertThat(result.profile()).isEqualTo(new AuthProfile(42L, "ordinary", "Ordinary User", "ordinary@example.test"));
    verify(client).login(new NewApiCredentials("ordinary", "correct-horse"));
    verify(sessionStore).create("upstream-session", 42L);
  }

  @Test
  void revokeDeletesTheServerSideRecordAndThenLogsOutUpstream() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    PortalSessionStore sessionStore = mock(PortalSessionStore.class);
    when(sessionStore.find("server-side-session-id"))
        .thenReturn(new PortalSessionRecord("server-side-session-id", "upstream-session", 42L));
    when(sessionStore.delete("server-side-session-id")).thenReturn(true);

    NewApiSession revoked = service(true, PublicationMode.PUBLIC, true, client, sessionStore)
        .revoke("server-side-session-id", 42L);

    assertThat(revoked).isEqualTo(new NewApiSession("upstream-session", 42L));
    // 先删除本地记录再退出上游：即使上游退出失败，旧 Cookie 也已失效。
    verify(sessionStore).delete("server-side-session-id");
    verify(client).logout(new NewApiSession("upstream-session", 42L));
  }

  @Test
  void revokeWithoutAServerSideRecordNeverCallsUpstream() {
    NewApiAuthenticationClient client = mock(NewApiAuthenticationClient.class);
    PortalSessionStore sessionStore = mock(PortalSessionStore.class);
    when(sessionStore.find("unknown-session")).thenReturn(null);

    assertThat(service(true, PublicationMode.PUBLIC, true, client, sessionStore)
            .revoke("unknown-session", 42L))
        .isNull();

    verifyNoInteractions(client);
    verify(sessionStore, never()).delete(org.mockito.ArgumentMatchers.anyString());
  }

  private static AuthApplicationService service(
      boolean adminEnabled, PublicationMode mode, boolean legalAvailable, NewApiAuthenticationClient client) {
    return service(adminEnabled, mode, legalAvailable, client, mock(PortalSessionStore.class));
  }

  private static AuthApplicationService service(
      boolean adminEnabled,
      PublicationMode mode,
      boolean legalAvailable,
      NewApiAuthenticationClient client,
      PortalSessionStore sessionStore) {
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
    return new AuthApplicationService(
        new RegistrationPolicyService(properties, publication, legal), client, sessionStore);
  }
}
