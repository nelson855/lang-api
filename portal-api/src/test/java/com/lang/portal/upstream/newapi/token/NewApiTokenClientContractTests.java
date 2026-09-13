package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenClientContractTests extends NewApiContractTestBase {

  private NewApiTokenClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTokenClient(exchange);
  }

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private static String listBody() {
    return """
        {"success":true,"message":"","data":{"page":1,"page_size":20,"total":1,"items":[
          {"id":7,"user_id":42,"key":"fN95**********CMHQ","status":1,"name":"probe-key-01",
           "created_time":1789180807,"accessed_time":1789180807,"expired_time":-1,
           "remain_quota":100000,"unlimited_quota":false,"model_limits_enabled":false,
           "model_limits":"","allow_ips":"","used_quota":0,"group":"","cross_group_retry":false,
           "DeletedAt":null,"futureField":"trim-me"}]}}
        """;
  }

  @Test
  void listUsesFrozenPathAndTrimsUnknownFields() throws Exception {
    server.enqueue(json(listBody()));
    NewApiTokenPage page = client().listTokens(SESSION, 1, 20);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).key()).contains("**********");
    assertThat(page.items().get(0).name()).isEqualTo("probe-key-01");

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/api/token/?page=1&page_size=20");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void searchUsesKeywordPathAndNeverTokenParam() throws Exception {
    server.enqueue(json(listBody()));
    client().searchTokens(SESSION, "probe", 1, 20);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).startsWith("/api/token/search");
    assertThat(request.getPath()).contains("keyword=%25probe%25");
    assertThat(request.getPath()).doesNotContain("token=");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void getReturnsSingleMaskedToken() throws Exception {
    server.enqueue(json("""
        {"success":true,"message":"","data":{"id":7,"user_id":42,"key":"fN95**********CMHQ",
          "status":1,"name":"probe-key-01","created_time":1789180807,"accessed_time":1789180807,
          "expired_time":-1,"remain_quota":100000,"unlimited_quota":false,
          "model_limits_enabled":false,"model_limits":"","allow_ips":"","used_quota":0,
          "group":"","cross_group_retry":false,"DeletedAt":null,"futureField":"trim-me"}}
        """));

    NewApiToken token = client().getToken(SESSION, 7L);

    assertThat(token.id()).isEqualTo(7L);
    assertThat(token.key()).contains("**********");
    RecordedRequest request = takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/token/7");
  }

  @Test
  void unauthorizedBecomesUnauthenticated() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().listTokens(SESSION, 1, 20))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void missingTokenBecomesNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().getToken(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void businessFailureBecomesUpstreamError() {
    server.enqueue(businessFailure());
    assertThatThrownBy(() -> client().listTokens(SESSION, 1, 20))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void nonJsonBecomesUpstreamError() {
    server.enqueue(nonJson());
    assertThatThrownBy(() -> client().getToken(SESSION, 7L))
        .isInstanceOf(UpstreamException.class);
  }
}
