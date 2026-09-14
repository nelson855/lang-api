package com.lang.portal.upstream.newapi.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiUserProfile;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiProfileUpdateClientContractTests extends NewApiContractTestBase {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final com.lang.portal.upstream.newapi.auth.NewApiSession SESSION =
      new com.lang.portal.upstream.newapi.auth.NewApiSession("upstream-session", 42L);

  private NewApiProfileUpdateClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiProfileUpdateClient(exchange, new NewApiAuthenticationClient(exchange));
  }

  private static String userJson() {
    return "{\"success\":true,\"message\":\"\",\"data\":{"
        + "\"id\":42,\"username\":\"newname\",\"display_name\":\"New Name\","
        + "\"email\":\"a@example.test\",\"status\":1}}";
  }

  @Test
  void profileOnlyUpdateUsesFixedPutPathAndWhitelistBody() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":null}"));
    server.enqueue(json(userJson()));

    NewApiUserProfile profile =
        client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null));

    assertThat(profile.username()).isEqualTo("newname");
    assertThat(profile.displayName()).isEqualTo("New Name");

    RecordedRequest put = takeRequest();
    assertThat(put.getMethod()).isEqualTo("PUT");
    assertThat(put.getPath()).isEqualTo("/api/user/self");
    assertThat(put.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(put.getHeader("New-Api-User")).isEqualTo("42");

    JsonNode body = MAPPER.readTree(put.getBody().readUtf8());
    assertThat(body.get("username").asText()).isEqualTo("newname");
    assertThat(body.get("display_name").asText()).isEqualTo("New Name");
    assertThat(body.get("original_password").asText()).isEqualTo("correct-123");
    assertThat(body.has("password")).isTrue();
    assertThat(body.has("email")).isFalse();
    assertThat(body.has("phone")).isFalse();
    assertThat(body.has("role")).isFalse();

    RecordedRequest reread = takeRequest();
    assertThat(reread.getMethod()).isEqualTo("GET");
    assertThat(reread.getPath()).isEqualTo("/api/user/self");
  }

  @Test
  void passwordChangeSendsNewPasswordInSameRequest() throws Exception {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":null}"));
    server.enqueue(json(userJson()));

    client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", "brand-new-123"));

    JsonNode body = MAPPER.readTree(takeRequest().getBody().readUtf8());
    assertThat(body.get("password").asText()).isEqualTo("brand-new-123");
    assertThat(body.get("original_password").asText()).isEqualTo("correct-123");
  }

  @Test
  void wrongCurrentPasswordMapsToInvalidArgumentWithoutReread() {
    server.enqueue(json("{\"success\":false,\"message\":\"原密码错误\",\"data\":null}"));

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "wrong-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(
            e ->
                ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT
                    && !String.valueOf(e.getMessage()).contains("wrong-123")
                    && !String.valueOf(e.getMessage()).contains("原密码错误"));
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void usernameConflictMapsToResourceConflict() {
    server.enqueue(json("{\"success\":false,\"message\":\"用户名已被占用\",\"data\":null}"));

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("taken", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.RESOURCE_CONFLICT);
  }

  @Test
  void unknownBusinessFailureMapsToUpstreamErrorWithoutLeaking() {
    server.enqueue(json("{\"success\":false,\"message\":\"原始上游错误\",\"data\":null}"));

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .matches(
            e ->
                e instanceof com.lang.portal.base.exception.UpstreamException
                    && !String.valueOf(e.getMessage()).contains("原始上游错误"));
  }

  @Test
  void disconnectAfterSendMapsToResultUnknown() {
    server.enqueue(disconnect());

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void rereadFailureAfterSuccessMapsToResultUnknown() {
    server.enqueue(json("{\"success\":true,\"message\":\"\",\"data\":null}"));
    server.enqueue(json("{\"success\":false,\"message\":\"boom\",\"data\":null}"));

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void illegalSuccessResponseMapsToResultUnknown() {
    server.enqueue(nonJson());

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void upstreamUnauthorizedMapsToUnauthenticated() {
    server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(401));

    assertThatThrownBy(
            () -> client().update(SESSION, new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void anonymousSessionRejectedBeforeUpstream() {
    assertThatThrownBy(
            () ->
                client()
                    .update(
                        new com.lang.portal.upstream.newapi.auth.NewApiSession("", 0L),
                        new ProfileUpdateCommand("newname", "New Name", "correct-123", null)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void commandToStringMasksCredentials() {
    ProfileUpdateCommand command =
        new ProfileUpdateCommand("newname", "New Name", "correct-123", "brand-new-123");

    assertThat(command.toString()).doesNotContain("correct-123", "brand-new-123");
  }
}
