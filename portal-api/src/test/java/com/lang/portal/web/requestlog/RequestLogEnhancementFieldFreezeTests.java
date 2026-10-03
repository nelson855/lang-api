package com.lang.portal.web.requestlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogMapper;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogPageRaw;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 冻结基线 p2-2026-09-22-a 未验证 protocol 与 first_token_time，本类固定“不得启用映射”的契约。
 */
class RequestLogEnhancementFieldFreezeTests {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private final NewApiLogClient logClient = mock(NewApiLogClient.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  /**
   * 上游同时带有同名的 protocol / first_token_time，还带有 model、is_stream、use_time 与未验证的 other。
   * 名称相似不构成语义证据，映射仍须保持关闭。
   */
  private static String rawBody(int type) {
    return """
        {"page":1,"page_size":20,"total":1,"items":[
          {"created_time":1789180800,"type":%s,"token_name":"probe-key-01",
           "model_name":"gpt-test","quota":500,"prompt_tokens":10,"completion_tokens":20,
           "use_time":3,"is_stream":true,"request_id":"req-1",
           "protocol":"OPENAI","first_token_time":820,
           "path":"/v1/chat/completions","other":"secret-other","content":"secret-prompt"}]}
        """
        .formatted(type);
  }

  private RequestLogDto firstDto(int upstreamType, NewApiLogResult expected) throws Exception {
    NewApiEnvelope<NewApiLogPageRaw> envelope =
        MAPPER.readValue(
            "{\"success\":true,\"message\":\"\",\"data\":" + rawBody(upstreamType) + "}",
            new TypeReference<NewApiEnvelope<NewApiLogPageRaw>>() {});
    NewApiLogPage mapped = NewApiLogMapper.mapPage(envelope.data(), expected);
    when(logClient.listSuccess(eq(SESSION), any(NewApiLogQuery.class))).thenReturn(mapped);
    when(logClient.listError(eq(SESSION), any(NewApiLogQuery.class))).thenReturn(mapped);
    var page =
        new RequestLogQueryService(logClient, properties)
            .list(SESSION, 1, 20, expected.name(), null, null, null, null, FIXED);
    assertThat(page.total()).isEqualTo(1);
    return page.items().get(0);
  }

  @Test
  void successLogKeepsProtocolAndFirstTokenLatencyNullDespiteSameNamedUpstreamFields()
      throws Exception {
    RequestLogDto dto = firstDto(2, NewApiLogResult.SUCCESS);

    assertThat(dto.protocol()).isNull();
    assertThat(dto.firstTokenLatencyMs()).isNull();
    // 基础字段仍按已验证映射保留
    assertThat(dto.model()).isEqualTo("gpt-test");
    assertThat(dto.stream()).isTrue();
    assertThat(dto.durationMs()).isEqualTo(3000L);
  }

  @Test
  void errorLogKeepsProtocolAndFirstTokenLatencyNullDespiteSameNamedUpstreamFields()
      throws Exception {
    RequestLogDto dto = firstDto(5, NewApiLogResult.ERROR);

    assertThat(dto.protocol()).isNull();
    assertThat(dto.firstTokenLatencyMs()).isNull();
  }
}