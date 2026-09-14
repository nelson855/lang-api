package com.lang.portal.upstream.newapi.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NewApiLogMappingTests {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private NewApiLogPage parse(String body, NewApiLogResult expected) throws Exception {
    NewApiEnvelope<NewApiLogPageRaw> envelope =
        MAPPER.readValue(body, new TypeReference<NewApiEnvelope<NewApiLogPageRaw>>() {});
    return NewApiLogMapper.mapPage(envelope.data(), expected);
  }

  private static String successBody() {
    return """
        {"page":1,"page_size":20,"total":1,"items":[
          {"created_time":1789180800,"type":2,"token_name":"probe-key-01",
           "model_name":"gpt-test","quota":500,"prompt_tokens":10,"completion_tokens":20,
           "use_time":3,"is_stream":true,"request_id":"req-1",
           "content":"secret-prompt","other":"secret-other","ip":"10.0.0.1",
           "channel":7,"group":"vip","user_id":42,"token_id":9,"futureField":"trim-me"}]}
        """;
  }

  @Test
  void mapsSuccessWithSecondsToMillis() throws Exception {
    NewApiLogPage page = parse(
        "{\"success\":true,\"message\":\"\",\"data\":" + successBody() + "}", NewApiLogResult.SUCCESS);

    assertThat(page.items()).hasSize(1);
    NewApiLogRecord record = page.items().get(0);
    assertThat(record.result()).isEqualTo(NewApiLogResult.SUCCESS);
    assertThat(record.keyName()).isEqualTo("probe-key-01");
    assertThat(record.model()).isEqualTo("gpt-test");
    assertThat(record.durationMs()).isEqualTo(3000L);
    assertThat(record.requestId()).isEqualTo("req-1");
  }

  @Test
  void mapsErrorWithNullableFields() throws Exception {
    String body = "{\"success\":true,\"message\":\"\",\"data\":"
        + successBody()
            .replace("\"type\":2", "\"type\":5")
            .replace("\"request_id\":\"req-1\"", "\"request_id\":null")
            .replace("\"token_name\":\"probe-key-01\"", "\"token_name\":null")
        + "}";

    NewApiLogPage page = parse(body, NewApiLogResult.ERROR);

    assertThat(page.items().get(0).result()).isEqualTo(NewApiLogResult.ERROR);
    assertThat(page.items().get(0).requestId()).isNull();
    assertThat(page.items().get(0).keyName()).isNull();
  }

  @Test
  void illegalEntryFailsWholePageWithoutLeakingBody() {
    String body = "{\"success\":true,\"message\":\"\",\"data\":"
        + successBody().replace("\"prompt_tokens\":10", "\"prompt_tokens\":-1")
        + "}";
    try {
      parse(body, NewApiLogResult.SUCCESS);
      throw new AssertionError("应当失败");
    } catch (UpstreamException e) {
      assertThat(e.errorCode()).isEqualTo(PortalErrorCode.UPSTREAM_ERROR);
      assertThat(e.getMessage()).doesNotContain("secret-prompt");
    } catch (Exception e) {
      throw new AssertionError("应当为 UpstreamException", e);
    }
  }

  @Test
  void numericOverflowFailsWholePage() {
    String body = "{\"success\":true,\"message\":\"\",\"data\":"
        + successBody().replace("\"use_time\":3", "\"use_time\":9999999999999999999")
        + "}";
    try {
      parse(body, NewApiLogResult.SUCCESS);
      throw new AssertionError("应当失败");
    } catch (UpstreamException e) {
      assertThat(e.errorCode()).isEqualTo(PortalErrorCode.UPSTREAM_ERROR);
    } catch (Exception e) {
      // Jackson 数值溢出同样视为整页不可信
      assertThat(e).isNotNull();
    }
  }
}
