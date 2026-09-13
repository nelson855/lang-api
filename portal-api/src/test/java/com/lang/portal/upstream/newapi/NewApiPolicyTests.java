package com.lang.portal.upstream.newapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.policy.NewApiCookiePolicy;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.config.PortalCommonProperties;
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

  @Test
  void cookiePolicyOnlyMapsTheUpstreamSessionToLangCookie() {
    PortalCommonProperties properties = new PortalCommonProperties();
    NewApiCookiePolicy policy = new NewApiCookiePolicy(properties);

    var cookies = policy.convert(List.of("session=upstream-session; HttpOnly", "tracking=discard-me"));

    assertThat(cookies).hasSize(1);
    assertThat(cookies.getFirst().getName()).isEqualTo("LANG_SESSION");
    assertThat(cookies.getFirst().getValue()).isEqualTo("upstream-session");
    assertThat(cookies.getFirst().getPath()).isEqualTo("/portal");
    assertThat(cookies.getFirst().isHttpOnly()).isTrue();
    assertThat(cookies.getFirst().getDomain()).isNull();
  }

  @Test
  void cookiePolicyCreatesAndExpiresBothLangSessionCookiesWithMatchingAttributes() {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().cookie().setSecure(true);
    NewApiCookiePolicy policy = new NewApiCookiePolicy(properties);

    var created = policy.createSessionCookies("upstream-session", 42L);
    assertThat(created).extracting(cookie -> cookie.getName()).containsExactly("LANG_SESSION", "LANG_UID");
    assertThat(created).allSatisfy(cookie -> {
      assertThat(cookie.getPath()).isEqualTo("/portal");
      assertThat(cookie.getDomain()).isNull();
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.isSecure()).isTrue();
      assertThat(cookie.getSameSite()).isEqualTo("Lax");
    });
    assertThat(created.getFirst().getValue()).isEqualTo("upstream-session");
    assertThat(created.get(1).getValue()).isEqualTo("42");

    var expired = policy.expireSessionCookies();
    assertThat(expired).extracting(cookie -> cookie.getName()).containsExactly("LANG_SESSION", "LANG_UID");
    assertThat(expired).allSatisfy(cookie -> {
      assertThat(cookie.getMaxAge().isZero()).isTrue();
      assertThat(cookie.getPath()).isEqualTo("/portal");
      assertThat(cookie.getDomain()).isNull();
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.isSecure()).isTrue();
      assertThat(cookie.getSameSite()).isEqualTo("Lax");
    });
  }
}
