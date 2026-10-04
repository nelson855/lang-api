package com.lang.portal.upstream.newapi.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregationSanitizerTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AggregationSanitizer sanitizer = new AggregationSanitizer();

  @Test
  void whitelistDropsNonPermittedFields() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "page":1,"page_size":20,"total":1,
              "items":[{"created_time":1789180800,"type":2,"token_name":"secret-key",
                        "model_name":"gpt-4","quota":500,"prompt_tokens":10,
                        "completion_tokens":20,"use_time":3,"is_stream":true,
                        "content":"secret-content","other":"secret-other","ip":"10.0.0.1",
                        "channel":7,"user_id":42,"future_unknown":"x"}]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeLogList(input);

    JsonNode item = sanitized.at("/data/items/0");
    assertThat(item.has("created_time")).isTrue();
    assertThat(item.has("type")).isTrue();
    assertThat(item.has("model_name")).isTrue();
    assertThat(item.has("quota")).isTrue();
    assertThat(item.has("prompt_tokens")).isTrue();
    assertThat(item.has("completion_tokens")).isTrue();
    assertThat(item.has("use_time")).isTrue();
    assertThat(item.has("is_stream")).isTrue();
    assertThat(item.has("content")).isFalse();
    assertThat(item.has("other")).isFalse();
    assertThat(item.has("future_unknown")).isFalse();
  }

  @Test
  void tokenNameMappedToStablePlaceholder() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "page":1,"page_size":20,"total":2,
              "items":[
                {"created_time":1789180800,"type":2,"token_name":"sk-abc-123",
                 "model_name":"gpt-4","quota":500,"prompt_tokens":10,
                 "completion_tokens":20,"use_time":3,"is_stream":false},
                {"created_time":1789180900,"type":2,"token_name":"sk-abc-123",
                 "model_name":"gpt-4","quota":300,"prompt_tokens":5,
                 "completion_tokens":10,"use_time":2,"is_stream":true}
              ]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeLogList(input);
    JsonNode items = sanitized.at("/data/items");
    String first = items.get(0).get("token_name").asText();
    String second = items.get(1).get("token_name").asText();
    assertThat(first).isEqualTo(second);
    assertThat(first).startsWith("<TOKEN_");
    assertThat(first).endsWith(">");
    assertThat(first).doesNotContain("sk-abc");
  }

  @Test
  void differentTokensGetDifferentPlaceholders() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "page":1,"page_size":20,"total":2,
              "items":[
                {"created_time":1789180800,"type":2,"token_name":"sk-alpha",
                 "model_name":"gpt-4","quota":500,"prompt_tokens":10,
                 "completion_tokens":20,"use_time":3,"is_stream":false},
                {"created_time":1789180900,"type":2,"token_name":"sk-beta",
                 "model_name":"gpt-4","quota":300,"prompt_tokens":5,
                 "completion_tokens":10,"use_time":2,"is_stream":true}
              ]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeLogList(input);
    JsonNode items = sanitized.at("/data/items");
    String first = items.get(0).get("token_name").asText();
    String second = items.get(1).get("token_name").asText();
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void userIdMappedToStableUserPlaceholder() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "page":1,"page_size":20,"total":1,
              "items":[{"created_time":1789180800,"type":2,"token_name":"k",
                        "model_name":"gpt-4","quota":100,"prompt_tokens":1,
                        "completion_tokens":1,"use_time":1,"is_stream":false,
                        "user_id":42}]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeLogList(input);
    String userVal = sanitized.at("/data/items/0/user_id").asText();
    assertThat(userVal).startsWith("<USER_");
    assertThat(userVal).endsWith(">");
  }

  @Test
  void topupSanitizerReplacesTransactionIdAndAmountStays() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "page":1,"page_size":10,"total":1,
              "items":[{"id":99,"user_id":42,"amount":1000,"currency":"USD",
                        "status":"success","trade_no":"TRADE-ABC-123",
                        "created_time":1789180800}]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeTopupRecords(input);
    JsonNode item = sanitized.at("/data/items/0");
    assertThat(item.get("trade_no").asText()).startsWith("<TRADE_");
    assertThat(item.get("trade_no").asText()).doesNotContain("TRADE-ABC");
    assertThat(item.get("amount").asLong()).isEqualTo(1000L);
    assertThat(item.get("status").asText()).isEqualTo("success");
    assertThat(item.get("user_id").asText()).startsWith("<USER_");
  }

  @Test
  void pricingSanitizerPreservesStructure() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "supported_endpoint":{},"vendors":[],
              "data":[{"model_name":"gpt-4","group_ratio":{"default":1},
                       "model_ratio":1,"model_price":0.002,
                       "owner":42,"created_by":99}],
              "usable_group":{"default":"默认分组"},
              "group_ratio":{"default":1},
              "pricing_version":"abc","auto_groups":["default"]}}
            """);

    JsonNode sanitized = sanitizer.sanitizePricing(input);
    assertThat(sanitized.has("success")).isTrue();
    assertThat(sanitized.has("message")).isTrue();
    assertThat(sanitized.has("data")).isTrue();
    assertThat(sanitized.at("/data/pricing_version").asText()).isEqualTo("abc");
    assertThat(sanitized.at("/data/vendors").isArray()).isTrue();
    assertThat(sanitized.at("/data/usable_group/default").asText()).isEqualTo("默认分组");
  }

  @Test
  void profileSanitizerReplacesUsernameAndEmail() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"success":true,"message":"ok","data":{
              "id":42,"username":"alice","email":"alice@example.com",
              "display_name":"Alice","role":1,"status":1,"quota":1000,
              "used_quota":500,"group":"default","aff_count":0}}
            """);

    JsonNode sanitized = sanitizer.sanitizeProfile(input);
    JsonNode data = sanitized.get("data");
    assertThat(data.get("username").asText()).startsWith("<USERNAME_");
    assertThat(data.get("email").asText()).startsWith("<EMAIL_");
    assertThat(data.get("id").asText()).startsWith("<USER_");
    assertThat(data.get("quota").asLong()).isEqualTo(1000L);
    assertThat(data.get("used_quota").asLong()).isEqualTo(500L);
  }

  @Test
  void mappingTableNotExposedAfterSanitize() {
    JsonNode input =
        MAPPER.createObjectNode().set(
            "data",
            MAPPER.createObjectNode().set(
                "items",
                MAPPER.createArrayNode().add(
                    MAPPER.createObjectNode().put("token_name", "sk-secret-1"))));

    sanitizer.sanitizeLogList(input);

    assertThatThrownBy(() -> sanitizer.getMappingSnapshot())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void consistentPlaceholderForSameValueAcrossCallsWithinInstance() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"data":{"items":[{"token_name":"sk-shared","user_id":7}]}}
            """);

    JsonNode first = sanitizer.sanitizeLogList(input);
    JsonNode second = sanitizer.sanitizeLogList(input);

    assertThat(first.at("/data/items/0/token_name").asText())
        .isEqualTo(second.at("/data/items/0/token_name").asText());
    assertThat(first.at("/data/items/0/user_id").asText())
        .isEqualTo(second.at("/data/items/0/user_id").asText());
  }

  @Test
  void preservesAssociationBetweenTokenAndUserAcrossItems() throws Exception {
    JsonNode input =
        MAPPER.readTree(
            """
            {"data":{"items":[
              {"token_name":"sk-a","user_id":7},
              {"token_name":"sk-b","user_id":8},
              {"token_name":"sk-a","user_id":7}
            ]}}
            """);

    JsonNode sanitized = sanitizer.sanitizeLogList(input);
    JsonNode items = sanitized.at("/data/items");
    String tokA0 = items.get(0).get("token_name").asText();
    String tokB1 = items.get(1).get("token_name").asText();
    String tokA2 = items.get(2).get("token_name").asText();
    String user7a = items.get(0).get("user_id").asText();
    String user8 = items.get(1).get("user_id").asText();
    String user7b = items.get(2).get("user_id").asText();

    assertThat(tokA0).isEqualTo(tokA2);
    assertThat(tokA0).isNotEqualTo(tokB1);
    assertThat(user7a).isEqualTo(user7b);
    assertThat(user7a).isNotEqualTo(user8);
  }
}
