package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

class NewApiTokenSearchContractTests extends NewApiContractTestBase {

  private NewApiExchange exchange() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    return new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
  }

  private static final TypeReference<NewApiEnvelope<Map<String, Object>>> PAGE_TYPE =
      new TypeReference<>() {};

  @Test
  void successReturnsMaskedPageAndSendsAuthHeaders() throws Exception {
    server.enqueue(json("""
        {"success":true,"message":"","data":{"page":1,"page_size":20,"total":1,"items":[
          {"id":1,"user_id":42,"key":"fN95**********CMHQ","status":1,"name":"probe-key-01",
           "created_time":1789180807,"accessed_time":1789180807,"expired_time":-1,
           "remain_quota":100000,"unlimited_quota":false,"model_limits_enabled":false,
           "model_limits":"","allow_ips":"","used_quota":0,"group":"","cross_group_retry":false}]}}
        """));

    String path = NewApiTokenSearch.searchPath("probe", 1, 20);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    Map<String, String> auth = Map.of("Cookie", "session=upstream-session", "New-Api-User", "42");

    Map<String, Object> data =
        exchange().executeRaw(op, null, auth, PAGE_TYPE).data();

    assertThat(data).containsEntry("total", 1);
    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).startsWith("/api/token/search");
    assertThat(request.getPath()).contains("keyword=");
    assertThat(request.getPath()).doesNotContain("token=");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
    assertThat(request.getHeader("Cookie")).doesNotContain("probe-key");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void emptyResultReturnsZeroTotal() {
    server.enqueue(json("""
        {"success":true,"message":"","data":{"page":1,"page_size":20,"total":0,"items":[]}}
        """));

    String path = NewApiTokenSearch.searchPath("no-such-name", 1, 20);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    Map<String, String> auth = Map.of("Cookie", "session=upstream-session", "New-Api-User", "42");

    Map<String, Object> data = exchange().executeRaw(op, null, auth, PAGE_TYPE).data();
    assertThat(data).containsEntry("total", 0);
  }

  @Test
  void keywordIsWrappedForContainsAndUpstreamEscapesItself() {
    String path = NewApiTokenSearch.searchPath("probe", 1, 20);
    assertThat(path).isEqualTo("/api/token/search?keyword=%25probe%25&page=1&page_size=20");
    String underscore = NewApiTokenSearch.searchPath("a_b", 1, 20);
    assertThat(underscore).contains("keyword=%25a_b%25");
    assertThat(underscore).doesNotContain("token=");
  }

  @Test
  void businessFailureWhenHttp200SuccessFalseMapsToUpstreamError() {
    server.enqueue(businessFailure());
    String path = NewApiTokenSearch.searchPath("probe", 1, 20);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    Map<String, String> auth = Map.of("Cookie", "session=upstream-session", "New-Api-User", "42");
    assertThatThrownBy(() -> exchange().execute(op, null, PAGE_TYPE))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void unauthorizedIsVisibleForAdapterToMap() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"success\":false}"));
    String path = NewApiTokenSearch.searchPath("probe", 1, 20);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    Map<String, String> auth = Map.of("Cookie", "session=stale", "New-Api-User", "42");
    var raw = exchange().executeRaw(op, null, auth, PAGE_TYPE);
    assertThat(raw.status()).isEqualTo(401);
    // 令牌适配器（2.9）须将 401 统一转为 UNAUTHENTICATED，此处先固定原始 401 可见
  }

  @Test
  void nonJsonMapsToUpstreamError() {
    server.enqueue(nonJson());
    String path = NewApiTokenSearch.searchPath("probe", 1, 20);
    NewApiOperation op = new NewApiOperation("token-search", HttpMethod.GET, path, true);
    assertThatThrownBy(() -> exchange()
            .execute(op, null, PAGE_TYPE))
        .isInstanceOf(UpstreamException.class);
  }
}
