package com.lang.portal.upstream.newapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.slf4j.LoggerFactory;

class NewApiAuthenticationContractTests extends NewApiContractTestBase {

  private NewApiAuthenticationClient client() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange = new NewApiExchange(RestClient.builder().build(), properties, new NewApiErrorTranslator());
    return new NewApiAuthenticationClient(exchange);
  }

  @Test
  void registerUsesFrozenRequestAndRejectsBusinessFailure() throws Exception {
    server.enqueue(businessFailure());

    assertThatThrownBy(() -> client().register(new NewApiCredentials("new-user", "correct-horse")))
        .isInstanceOf(UpstreamException.class)
        .matches(error -> ((UpstreamException) error).errorCode() == PortalErrorCode.UPSTREAM_ERROR);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/user/register");
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"username\":\"new-user\",\"password\":\"correct-horse\"}");
  }

  @Test
  void loginUsesFrozenRequestAndNormalizesBusinessFailure() throws Exception {
    server.enqueue(businessFailure());

    assertThatThrownBy(() -> client().login(new NewApiCredentials("known-user", "wrong-password")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.INVALID_CREDENTIALS);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/user/login");
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"username\":\"known-user\",\"password\":\"wrong-password\"}");
  }

  @Test
  void invalidCredentialEventDoesNotContainCredentials() {
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    ((Logger) LoggerFactory.getLogger(NewApiAuthenticationClient.class)).addAppender(events);
    server.enqueue(businessFailure());

    assertThatThrownBy(() -> client().login(new NewApiCredentials("ordinary@example.test", "correct-horse")))
        .isInstanceOf(PortalException.class);

    String output = events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    assertThat(output).contains("event=invalid_credentials")
        .doesNotContain("ordinary", "correct-horse", "example.test");
  }

  @Test
  void currentUserMapsUpstreamUnauthorizedToExpiredSession() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false,\"message\":\"expired\"}"));

    assertThatThrownBy(() -> client().currentUser(new NewApiSession("upstream-session", 42L)))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.UNAUTHENTICATED);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user/self");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void currentUserMapsDisabledUpstreamProfileToExpiredSession() {
    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":42,\"username\":\"ordinary\",\"status\":2}}"));

    assertThatThrownBy(() -> client().currentUser(new NewApiSession("upstream-session", 42L)))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void logoutUsesFrozenGetAndTreatsUnauthorizedAsAlreadyLoggedOut() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false,\"message\":\"expired\"}"));

    client().logout(new NewApiSession("upstream-session", 42L));

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user/logout");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void loginRejectsMalformedSuccessResponse() {
    server.enqueue(nonJson());

    assertThatThrownBy(() -> client().login(new NewApiCredentials("known-user", "correct-horse")))
        .isInstanceOf(UpstreamException.class)
        .matches(error -> ((UpstreamException) error).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void logoutPropagatesUnavailableUpstream() {
    server.enqueue(disconnect());

    assertThatThrownBy(() -> client().logout(new NewApiSession("upstream-session", 42L)))
        .isInstanceOf(UpstreamException.class)
        .matches(error -> ((UpstreamException) error).errorCode() == PortalErrorCode.UPSTREAM_UNAVAILABLE);
  }

  @Test
  void loginAcceptsOnlyOneSessionCookieAndAllowedUserFields() {
    server.enqueue(json("""
        {"success":true,"message":"ok","data":{"id":7,"username":"ordinary","display_name":"Ordinary User","email":"ordinary@example.test","role":100,"quota":999}}
        """).addHeader("Set-Cookie", "session=upstream-session; HttpOnly")
        .addHeader("Set-Cookie", "tracking=discard-me"));

    NewApiLoginResult result = client().login(new NewApiCredentials("ordinary", "correct-horse"));

    assertThat(result.session().value()).isEqualTo("upstream-session");
    assertThat(result.session().userId()).isEqualTo(7L);
    assertThat(result.user()).isEqualTo(new NewApiUserProfile(7L, "ordinary", "Ordinary User", "ordinary@example.test"));
  }

  @Test
  void loginRejectsMissingOrDuplicateSessionCookie() {
    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":7,\"username\":\"ordinary\"}}"));
    assertThatThrownBy(() -> client().login(new NewApiCredentials("ordinary", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.UPSTREAM_ERROR);

    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":7,\"username\":\"ordinary\"}}")
        .addHeader("Set-Cookie", "session=first")
        .addHeader("Set-Cookie", "session=second"));
    assertThatThrownBy(() -> client().login(new NewApiCredentials("ordinary", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void loginRejectsIllegalUserIdWithoutBuildingPartialSession() {
    server.enqueue(json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":0,\"username\":\"ordinary\"}}")
        .addHeader("Set-Cookie", "session=upstream-session"));

    assertThatThrownBy(() -> client().login(new NewApiCredentials("ordinary", "correct-horse")))
        .isInstanceOf(PortalException.class)
        .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void loginNormalizesUnknownPasswordAndDisabledFailuresWithoutLeakingUpstreamMessage() {
    for (String message : new String[] {"unknown account", "wrong password", "account disabled"}) {
      server.enqueue(json("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}"));
      assertThatThrownBy(() -> client().login(new NewApiCredentials("ordinary", "incorrect")))
          .isInstanceOf(PortalException.class)
          .matches(error -> ((PortalException) error).errorCode() == PortalErrorCode.INVALID_CREDENTIALS)
          .hasMessage(PortalErrorCode.INVALID_CREDENTIALS.message())
          .hasMessageNotContaining(message);
    }
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void semanticUserProjectionHasNoRoleOrTokenField() {
    assertThat(NewApiUserProfile.class.getRecordComponents())
        .extracting(component -> component.getName())
        .containsExactly("id", "username", "displayName", "email");
  }
}
