package com.lang.portal.upstream.newapi.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import org.junit.jupiter.api.Test;

class AggregationLogTokenIdTests {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private NewApiLogPage parse(String body, NewApiLogResult expected) throws Exception {
    NewApiEnvelope<NewApiLogPageRaw> envelope =
        MAPPER.readValue(body, new TypeReference<NewApiEnvelope<NewApiLogPageRaw>>() {});
    return NewApiLogMapper.mapPage(envelope.data(), expected);
  }

  private static String bodyWithTokenId(String tokenIdFragment) {
    String tokenPart = tokenIdFragment == null || tokenIdFragment.isBlank() ? "" : "," + tokenIdFragment;
    return "{\"success\":true,\"message\":\"\",\"data\":"
        + "{\"page\":1,\"page_size\":20,\"total\":1,\"items\":["
        + "{\"created_time\":1789180800,\"type\":2,\"token_name\":\"probe-key-01\","
        + "\"model_name\":\"gpt-test\",\"quota\":500,\"prompt_tokens\":10,\"completion_tokens\":20,"
        + "\"use_time\":3,\"is_stream\":true,\"request_id\":\"req-1\""
        + tokenPart
        + "}]}}";
  }

  @Test
  void presentNumericTokenIdIsPreserved() throws Exception {
    NewApiLogPage page = parse(bodyWithTokenId("\"token_id\":9"), NewApiLogResult.SUCCESS);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).tokenId()).isEqualTo(9L);
  }

  @Test
  void missingTokenIdKeepsMissingSemantics() throws Exception {
    NewApiLogPage page = parse(bodyWithTokenId(""), NewApiLogResult.SUCCESS);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).tokenId()).isNull();
  }

  @Test
  void illegalTokenIdTypeFailsWholePage() {
    assertThatThrownBy(() -> parse(bodyWithTokenId("\"token_id\":\"<TOKENID_1>\""), NewApiLogResult.SUCCESS))
        .isInstanceOf(Exception.class);
  }

  @Test
  void realDesensitizedFixtureKeepsTokenIdPresence() throws Exception {
    java.nio.file.Path repo = locateRepoRoot();
    java.nio.file.Path fixture = repo.resolve("docs/new-api/samples/aggregation/log-self.nonempty.json");
    assertThat(fixture).exists();
    com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(java.nio.file.Files.newInputStream(fixture));
    com.fasterxml.jackson.databind.JsonNode items = root.path("data").path("items");
    assertThat(items.isArray()).isTrue();
    assertThat(items.size()).isGreaterThan(0);
    for (com.fasterxml.jackson.databind.JsonNode item : items) {
      assertThat(item.has("token_id")).as("desensitized fixture must preserve token_id presence").isTrue();
    }
  }

  private static java.nio.file.Path locateRepoRoot() {
    java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (dir != null && !java.nio.file.Files.isDirectory(dir.resolve("docs/new-api"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException("cannot locate repo root");
    }
    return dir;
  }
}
