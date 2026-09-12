package com.lang.portal.base.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResponseContractTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void successUsesRequestIdAndData() throws Exception {
    ApiResponse<String> response = ApiResponses.ok("req_abc12345", "hello");
    JsonNode json = mapper.valueToTree(response);
    assertThat(json.has("requestId")).isTrue();
    assertThat(json.get("requestId").asText()).isEqualTo("req_abc12345");
    assertThat(json.get("data").asText()).isEqualTo("hello");
    assertThat(json.has("error")).isFalse();
    assertThat(json.has("success")).isFalse();
    assertThat(json.has("message")).isFalse();
  }

  @Test
  void failureUsesRequestIdAndErrorCode() throws Exception {
    ApiResponse<Void> response = ApiResponses.failure("req_xyz", "NOT_FOUND", "内容不存在");
    JsonNode json = mapper.valueToTree(response);
    assertThat(json.get("requestId").asText()).isEqualTo("req_xyz");
    assertThat(json.get("error").get("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(json.get("error").get("message").asText()).isNotBlank();
    assertThat(json.has("data")).isFalse();
  }

  @Test
  void pageDataKeepsInvariants() throws Exception {
    PageData<String> page = PageData.of(List.of("a"), 1, 20, 1);
    JsonNode json = mapper.valueToTree(ApiResponses.ok("req_1", page));
    JsonNode data = json.get("data");
    assertThat(data.get("page").asInt()).isEqualTo(1);
    assertThat(data.get("pageSize").asInt()).isEqualTo(20);
    assertThat(data.get("total").asLong()).isEqualTo(1);
    assertThat(data.get("items").size()).isEqualTo(1);
  }
}
