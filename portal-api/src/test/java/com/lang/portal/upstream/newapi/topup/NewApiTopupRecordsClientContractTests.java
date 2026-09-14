package com.lang.portal.upstream.newapi.topup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.PageData;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.web.account.TopupRecord;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTopupRecordsClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiTopupRecordsClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTopupRecordsClient(exchange);
  }

  @Test
  void recordsUseFixedPathWithPaginationAndDualAuth() throws Exception {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"page\":1,\"page_size\":20,\"total\":1,\"items\":[{"
            + "\"trade_no\":\"ORD-1\",\"amount\":10,\"payment_method\":\"alipay\","
            + "\"create_time\":1700000000,\"complete_time\":1700000100,\"status\":\"success\","
            + "\"id\":99,\"user_id\":42,\"payment_provider\":\"x\",\"money\":\"1\"}]}}"));

    PageData<TopupRecord> page = client().page(SESSION, 1, 20);

    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).orderId()).isEqualTo("ORD-1");
    assertThat(page.items().get(0).status()).isEqualTo("SUCCEEDED");

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/user/topup/self?page=1&page_size=20");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void emptyPageMapsToEmptyItems() {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"page\":1,\"page_size\":20,\"total\":0,\"items\":[]}}"));

    PageData<TopupRecord> page = client().page(SESSION, 1, 20);

    assertThat(page.total()).isZero();
    assertThat(page.items()).isEmpty();
  }

  @Test
  void recordsReturnInCreateTimeDescending() {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"page\":1,\"page_size\":20,\"total\":2,\"items\":[{"
            + "\"trade_no\":\"ORD-OLD\",\"amount\":10,\"payment_method\":\"alipay\","
            + "\"create_time\":1700000000,\"complete_time\":1700000100,\"status\":\"success\"},{"
            + "\"trade_no\":\"ORD-NEW\",\"amount\":20,\"payment_method\":\"wxpay\","
            + "\"create_time\":1700000500,\"complete_time\":1700000600,\"status\":\"success\"}]}}"));

    PageData<TopupRecord> page = client().page(SESSION, 1, 20);

    assertThat(page.items()).extracting(TopupRecord::orderId).containsExactly("ORD-NEW", "ORD-OLD");
  }

  @Test
  void businessFailureMapsToUpstreamErrorWithoutLeakingBody() {
    server.enqueue(businessFailure());

    assertThatThrownBy(() -> client().page(SESSION, 1, 20))
        .matches(
            e ->
                e instanceof com.lang.portal.base.exception.UpstreamException
                    && !String.valueOf(e.getMessage()).contains("原始上游错误"));
  }

  @Test
  void illegalRecordFailsWholePage() {
    server.enqueue(
        json("{\"success\":true,\"message\":\"\",\"data\":{"
            + "\"page\":1,\"page_size\":20,\"total\":1,\"items\":[{"
            + "\"trade_no\":\"ORD-9\",\"amount\":10,\"payment_method\":\"alipay\","
            + "\"create_time\":1700000000,\"complete_time\":1700000100,\"status\":\"weird\"}]}}"));

    assertThatThrownBy(() -> client().page(SESSION, 1, 20))
        .matches(e -> e instanceof com.lang.portal.base.exception.UpstreamException);
  }

  @Test
  void unauthorizedMapsToUnauthenticated() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThatThrownBy(() -> client().page(SESSION, 1, 20))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void disconnectMapsToUpstreamUnavailable() {
    server.enqueue(disconnect());

    assertThatThrownBy(() -> client().page(SESSION, 1, 20))
        .matches(
            e ->
                e instanceof com.lang.portal.base.exception.UpstreamException
                    && ((com.lang.portal.base.exception.UpstreamException) e).errorCode()
                        == com.lang.portal.base.exception.PortalErrorCode.UPSTREAM_UNAVAILABLE);
  }
}
