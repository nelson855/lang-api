package com.lang.portal.upstream.newapi.topup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.web.account.TopupCapability;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTopupInfoClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiTopupInfoClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTopupInfoClient(exchange);
  }

  @Test
  void closedConfigUsesFixedPathAndDualAuthAndIgnoresResidualMethods() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"enable_online_topup\":false,\"enable_stripe_topup\":false,"
            + "\"enable_creem_topup\":false,\"enable_waffo_topup\":false,"
            + "\"enable_waffo_pancake_topup\":false,"
            + "\"pay_methods\":[{\"type\":\"alipay\"}],\"min_topup\":1}}"));

    TopupCapability capability = client().capability(SESSION);

    assertThat(capability.enabled()).isFalse();
    assertThat(capability.reason()).isEqualTo("NOT_CONFIGURED");
    assertThat(capability.methods()).isEmpty();

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user/topup/info");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void anyEnabledChannelConvergesToUnsupported() {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"enable_online_topup\":true,\"enable_stripe_topup\":false,"
            + "\"enable_creem_topup\":false,\"enable_waffo_topup\":false,"
            + "\"enable_waffo_pancake_topup\":false}}"));

    TopupCapability capability = client().capability(SESSION);

    assertThat(capability.enabled()).isFalse();
    assertThat(capability.reason()).isEqualTo("UNSUPPORTED_PROVIDER");
  }

  @Test
  void unauthorizedMapsToUnauthenticated() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> client().capability(SESSION))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void businessFailureMapsToUpstreamError() {
    server.enqueue(businessFailure());

    assertThatThrownBy(() -> client().capability(SESSION))
        .matches(e -> e instanceof com.lang.portal.base.exception.UpstreamException);
  }

  @Test
  void disconnectMapsToUpstreamUnavailable() {
    server.enqueue(disconnect());

    assertThatThrownBy(() -> client().capability(SESSION))
        .matches(
            e ->
                e instanceof com.lang.portal.base.exception.UpstreamException
                    && ((com.lang.portal.base.exception.UpstreamException) e).errorCode()
                        == PortalErrorCode.UPSTREAM_UNAVAILABLE);
  }

  @Test
  void illegalResponseMapsToUpstreamError() {
    server.enqueue(nonJson());

    assertThatThrownBy(() -> client().capability(SESSION))
        .matches(e -> e instanceof com.lang.portal.base.exception.UpstreamException);
  }

  @Test
  void anonymousSessionRejectedBeforeUpstream() {
    assertThatThrownBy(() -> client().capability(new NewApiSession("", 0L)))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }
}
