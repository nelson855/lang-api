package com.lang.portal.upstream.newapi.log;

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
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiLogClientContractTests extends NewApiContractTestBase {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private NewApiLogClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiLogClient(exchange);
  }

  private static String pageBody() {
    return """
        {"success":true,"message":"","data":{"page":1,"page_size":20,"total":1,"items":[
          {"created_time":1789180800,"type":2,"token_name":"probe-key-01",
           "model_name":"gpt-test","quota":500,"prompt_tokens":10,"completion_tokens":20,
           "use_time":3,"is_stream":true,"request_id":"req-1",
           "content":"secret-prompt","other":"secret-other","ip":"10.0.0.1",
           "channel":7,"group":"vip","user_id":42,"token_id":9,"futureField":"trim-me"}]}}
        """;
  }

  @Test
  void successUsesFrozenLogPathWithType2() throws Exception {
    server.enqueue(json(pageBody()));
    NewApiLogQuery query = NewApiLogQuery.of(1, 20, null, null, 1789180800L, 1789184399L);

    NewApiLogPage page = client().listSuccess(SESSION, query);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).hasSize(1);

    RecordedRequest request = takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).startsWith("/api/log/self");
    assertThat(request.getPath()).contains("type=2");
    assertThat(request.getPath()).contains("page=1");
    assertThat(request.getPath()).contains("page_size=20");
    assertThat(request.getHeader("Cookie")).isEqualTo("session=upstream-session");
    assertThat(request.getHeader("New-Api-User")).isEqualTo("42");
  }

  @Test
  void errorUsesType5() throws Exception {
    server.enqueue(json(pageBody().replace("\"type\":2", "\"type\":5")));
    NewApiLogQuery query = NewApiLogQuery.of(2, 20, null, null, 1789180800L, 1789184399L);

    client().listError(SESSION, query);

    RecordedRequest request = takeRequest();
    assertThat(request.getPath()).contains("type=5");
    assertThat(request.getPath()).contains("page=2");
  }

  @Test
  void businessFailureBecomesUpstreamError() {
    server.enqueue(businessFailure());
    NewApiLogQuery query = NewApiLogQuery.of(1, 20, null, null, 1789180800L, 1789184399L);

    assertThatThrownBy(() -> client().listSuccess(SESSION, query))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void unauthorizedBecomesUnauthenticated() {
    server.enqueue(
        new okhttp3.mockwebserver.MockResponse().setResponseCode(401).setBody("{\"success\":false}"));
    NewApiLogQuery query = NewApiLogQuery.of(1, 20, null, null, 1789180800L, 1789184399L);

    assertThatThrownBy(() -> client().listSuccess(SESSION, query))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void illegalEntryFailsWholePageWithoutLeakingBody() {
    server.enqueue(json(pageBody().replace("\"prompt_tokens\":10", "\"prompt_tokens\":-1")));
    NewApiLogQuery query = NewApiLogQuery.of(1, 20, null, null, 1789180800L, 1789184399L);

    assertThatThrownBy(() -> client().listSuccess(SESSION, query))
        .isInstanceOf(UpstreamException.class)
        .matches(
            e ->
                ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR
                    && (e.getMessage() == null || !e.getMessage().contains("secret-prompt")));
  }
}
