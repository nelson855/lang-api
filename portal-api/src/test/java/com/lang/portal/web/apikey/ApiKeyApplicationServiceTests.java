package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.token.NewApiCreateTokenCommand;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.NewApiUpdateTokenCommand;
import com.lang.portal.upstream.newapi.token.SensitiveSecret;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyApplicationServiceTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final NewApiTokenClient client = mock(NewApiTokenClient.class);
  private final ApiKeyApplicationService service = new ApiKeyApplicationService(client, new ApiKeySecurityEvents());

  private static String futureIso() {
    return Instant.now().plusSeconds(3600).toString();
  }

  @Test
  void createLimitedSendsOnceAndReturnsNoSecret() throws Exception {
    String expires = futureIso();
    var request = new CreateApiKeyRequest("key-1", false, 100L, expires, List.of("gpt-4o"), List.of("1.1.1.1"));

    ApiKeyCreateResponse response = service.create(SESSION, request);

    assertThat(response.created()).isTrue();
    verify(client, times(1)).createToken(any(), any());
    JsonNode json = MAPPER.valueToTree(response);
    assertThat(json.fieldNames()).toIterable().containsExactly("created");
    assertThat(json.toString()).doesNotContain("secret", "\"id\"", "sk-");
  }

  @Test
  void createBuildsUpstreamCommandWithConvertedExpiry() {
    String expires = futureIso();
    var request = new CreateApiKeyRequest("key-1", false, 100L, expires, List.of(), List.of());

    service.create(SESSION, request);

    var expected = new NewApiCreateTokenCommand(
        "key-1", false, 100L, Instant.parse(expires).getEpochSecond(), List.of(), List.of());
    verify(client, times(1)).createToken(SESSION, expected);
  }

  @Test
  void createUnlimitedNeverExpire() {
    var request = new CreateApiKeyRequest("big", true, null, null, null, null);

    service.create(SESSION, request);

    verify(client, times(1))
        .createToken(SESSION, new NewApiCreateTokenCommand("big", true, 0L, null, List.of(), List.of()));
  }

  @Test
  void createRejectsContradictoryAndPastExpiryWithoutUpstream() {
    var contradictory = new CreateApiKeyRequest("key-1", true, 100L, null, null, null);
    assertThatThrownBy(() -> service.create(SESSION, contradictory))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    var past = new CreateApiKeyRequest("key-1", false, 1L, Instant.now().minusSeconds(10).toString(), null, null);
    assertThatThrownBy(() -> service.create(SESSION, past))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    var badIp = new CreateApiKeyRequest("key-1", false, 1L, null, null, List.of("example.com"));
    assertThatThrownBy(() -> service.create(SESSION, badIp))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(client);
  }

  @Test
  void createLimitReachedPropagates() {
    doThrow(new PortalException(PortalErrorCode.API_KEY_LIMIT_REACHED))
        .when(client).createToken(any(), any());
    var request = new CreateApiKeyRequest("key-1", false, 1L, null, null, null);
    assertThatThrownBy(() -> service.create(SESSION, request))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.API_KEY_LIMIT_REACHED);
  }

  @Test
  void updateMergesSingleField() {
    var request = new UpdateApiKeyRequest("renamed", null, null, null, null, null);

    ApiKeyUpdateResponse response = service.update(SESSION, 7L, request);

    assertThat(response.updated()).isTrue();
    verify(client, times(1))
        .updateToken(SESSION, 7L, new NewApiUpdateTokenCommand("renamed", null, null, null, null, null));
  }

  @Test
  void updateRejectsBadLimitsAndUnknown() {
    var badIp = new UpdateApiKeyRequest(null, null, null, null, null, List.of("1.1.1.0/24"));
    assertThatThrownBy(() -> service.update(SESSION, 7L, badIp))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verifyNoInteractions(client);

    doThrow(new PortalException(PortalErrorCode.NOT_FOUND)).when(client).updateToken(any(), anyLong(), any());
    var request = new UpdateApiKeyRequest("renamed", null, null, null, null, null);
    assertThatThrownBy(() -> service.update(SESSION, 999L, request))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void updateResultUnknownPropagates() {
    doThrow(new PortalException(PortalErrorCode.OPERATION_RESULT_UNKNOWN))
        .when(client).updateToken(any(), anyLong(), any());
    var request = new UpdateApiKeyRequest("renamed", null, null, null, null, null);
    assertThatThrownBy(() -> service.update(SESSION, 7L, request))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void statusBothDirections() {
    assertThat(service.updateStatus(SESSION, 7L, false).enabled()).isFalse();
    verify(client, times(1)).updateStatus(SESSION, 7L, false);
    assertThat(service.updateStatus(SESSION, 7L, true).enabled()).isTrue();
    verify(client, times(1)).updateStatus(SESSION, 7L, true);
  }

  @Test
  void statusConflictAndNullRejected() {
    doThrow(new PortalException(PortalErrorCode.RESOURCE_CONFLICT)).when(client).updateStatus(any(), anyLong(), anyBoolean());
    assertThatThrownBy(() -> service.updateStatus(SESSION, 7L, true))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.RESOURCE_CONFLICT);
    assertThatThrownBy(() -> service.updateStatus(SESSION, 7L, null))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void deleteReturnsConfirmation() {
    ApiKeyDeleteResponse response = service.delete(SESSION, 7L);
    assertThat(response.deleted()).isTrue();
    verify(client, times(1)).deleteToken(SESSION, 7L);
  }

  @Test
  void deleteNotFoundAndUnknownDistinguished() {
    doThrow(new PortalException(PortalErrorCode.NOT_FOUND)).when(client).deleteToken(any(), anyLong());
    assertThatThrownBy(() -> service.delete(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void revealReturnsSecretAndClearsServerCopy() throws Exception {
    SensitiveSecret held = SensitiveSecret.of("sk-abc");
    when(client.revealToken(SESSION, 7L)).thenReturn(held);

    ApiKeyRevealResponse response = service.reveal(SESSION, 7L);

    assertThat(response.secret()).isEqualTo("sk-abc");
    assertThatThrownBy(held::asString).isInstanceOf(IllegalStateException.class);
    JsonNode json = MAPPER.valueToTree(response);
    assertThat(json.fieldNames()).toIterable().containsExactly("secret");
  }

  @Test
  void revealBadIdNeverTouchesUpstream() {
    assertThatThrownBy(() -> service.reveal(SESSION, 0L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
    verifyNoInteractions(client);
  }

  @Test
  void revealFailureCarriesNoSecret() {
    doThrow(new PortalException(PortalErrorCode.NOT_FOUND)).when(client).revealToken(any(), anyLong());
    assertThatThrownBy(() -> service.reveal(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND
            && !String.valueOf(e.getMessage()).contains("sk-"));
  }
}
