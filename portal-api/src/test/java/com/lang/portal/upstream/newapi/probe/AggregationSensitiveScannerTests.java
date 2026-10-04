package com.lang.portal.upstream.newapi.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AggregationSensitiveScannerTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AggregationSensitiveScanner scanner = new AggregationSensitiveScanner();

  @Test
  void cleanSanitizedJsonPasses() throws Exception {
    String json =
        """
        {"success":true,"message":"ok","data":{"items":[
          {"created_time":1789180800,"type":2,"token_name":"<TOKEN_1>",
           "model_name":"gpt-4","quota":500,"prompt_tokens":10,
           "completion_tokens":20,"use_time":3,"is_stream":true,
           "user_id":"<USER_1>","channel":"<CHANNEL_1>"}]}}
        """;
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void detectsCookieHeaderString() throws Exception {
    String json = "{\"data\":{\"header\":\"Cookie: session=abc123\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations()).anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.COOKIE_HEADER);
  }

  @Test
  void detectsAuthorizationBearer() throws Exception {
    String json = "{\"data\":{\"auth\":\"Authorization: Bearer sk-abcdef\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.AUTHORIZATION_BEARER);
  }

  @Test
  void detectsRawApiKeyPattern() throws Exception {
    String json = "{\"data\":{\"key\":\"sk-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.RAW_API_KEY);
  }

  @Test
  void detectsEmailAddress() throws Exception {
    String json = "{\"data\":{\"contact\":\"alice@example.com\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.EMAIL);
  }

  @Test
  void detectsPhoneNumber() throws Exception {
    String json = "{\"data\":{\"phone\":\"13812345678\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.PHONE);
  }

  @Test
  void detectsIpv4Address() throws Exception {
    String json = "{\"data\":{\"ip\":\"192.168.1.100\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.IP_ADDRESS);
  }

  @Test
  void ignoresLoopbackAndPrivateIpInsideJsonValueIfItIsNotAField() throws Exception {
    String json = "{\"data\":{\"note\":\"loopback test 127.0.0.1 ok\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.violations())
        .noneMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.IP_ADDRESS);
  }

  @Test
  void detectsRequestContentField() throws Exception {
    String json = "{\"data\":{\"content\":\"secret user prompt content\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.REQUEST_CONTENT);
  }

  @Test
  void detectsInternalChannelField() throws Exception {
    String json = "{\"data\":{\"channel\":\"internal-channel-7\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.INTERNAL_CHANNEL);
  }

  @Test
  void detectsSupplierSecretField() throws Exception {
    String json = "{\"data\":{\"supplier_secret\":\"AKIAIOSFODNN7EXAMPLE\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.SUPPLIER_SECRET);
  }

  @Test
  void scanOrThrowPassesWhenClean() throws Exception {
    String json = "{\"success\":true,\"data\":{\"items\":[]}}";
    scanner.scanOrThrow(MAPPER.readTree(json));
  }

  @Test
  void scanOrThrowThrowsWithDetails() throws Exception {
    String json = "{\"data\":{\"contact\":\"alice@example.com\",\"key\":\"sk-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789\"}}";
    assertThatThrownBy(() -> scanner.scanOrThrow(MAPPER.readTree(json)))
        .isInstanceOf(AggregationProbeException.class)
        .hasMessageContaining("sensitive");
  }

  @Test
  void detectsLongRandomStringInKnownSecretFields() throws Exception {
    String json = "{\"data\":{\"session\":\"a8f4e9c1d2b34a56b7c8d9e0f1a2b3c4d\"}}";
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.SESSION_TOKEN);
  }

  @Test
  void nestedFieldsAreScanned() throws Exception {
    String json =
        """
        {"outer":{"inner":{"deep_email":"bob@example.org"}}}
        """;
    var result = scanner.scan(MAPPER.readTree(json));
    assertThat(result.clean()).isFalse();
    assertThat(result.violations())
        .anyMatch(v -> v.kind() == AggregationSensitiveScanner.ViolationKind.EMAIL);
  }
}
