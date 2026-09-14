package com.lang.portal.upstream.newapi.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiBalanceClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiBalanceClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiBalanceClient(exchange);
  }

  @Test
  void balanceUsesFrozenUserSelfPathAndReturnsQuotaOnly() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"id\":42,\"username\":\"probe\",\"quota\":500000,"
            + "\"password\":\"secret\",\"futureField\":\"trim-me\"}}"));

    long quota = client().currentQuota(SESSION);

    assertThat(quota).isEqualTo(500000L);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user/self");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void negativeQuotaFailsWithoutLeaking() {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{\"id\":42,\"quota\":-5}}"));

    assertThatThrownBy(() -> client().currentQuota(SESSION))
        .matches(
            e ->
                e instanceof com.lang.portal.base.exception.UpstreamException
                    && ((com.lang.portal.base.exception.UpstreamException) e).errorCode()
                        == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void anonymousSessionIsRejectedBeforeUpstream() {
    assertThatThrownBy(
            () -> client().currentQuota(new NewApiSession("", 0L)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }
}
