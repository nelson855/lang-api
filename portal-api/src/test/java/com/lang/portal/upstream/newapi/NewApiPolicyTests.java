package com.lang.portal.upstream.newapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.policy.NewApiHeaderPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class NewApiPolicyTests extends NewApiContractTestBase {

  @Test
  void publicOperationOnlySendsWhitelistedHeaders() {
    NewApiOperation op = new NewApiOperation("probe", HttpMethod.GET, "/api/status", false);
    Map<String, String> headers = NewApiHeaderPolicy.requestHeaders(op, "req_123", Map.of("Cookie", "s=1", "New-Api-User", "u"));
    assertThat(headers).containsKeys("Accept", "Content-Type", "X-Request-Id");
    assertThat(headers).doesNotContainKeys("Cookie", "New-Api-User", "Authorization", "Host");
  }

  @Test
  void errorTranslatorMapsBusinessFailure() {
    NewApiErrorTranslator translator = new NewApiErrorTranslator();
    assertThat(translator.translate(200, false).errorCode().name()).isEqualTo("UPSTREAM_ERROR");
    assertThat(translator.translate(503, null).errorCode().name()).isEqualTo("UPSTREAM_UNAVAILABLE");
    assertThat(translator.translate(504, null).errorCode().name()).isEqualTo("UPSTREAM_TIMEOUT");
  }
}
