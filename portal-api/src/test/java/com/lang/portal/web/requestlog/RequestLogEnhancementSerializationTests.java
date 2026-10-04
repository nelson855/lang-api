package com.lang.portal.web.requestlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.PageData;
import com.lang.portal.base.security.PortalSessionAuthenticationFilter;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.log.NewApiLogClient;
import com.lang.portal.upstream.newapi.log.NewApiLogPage;
import com.lang.portal.upstream.newapi.log.NewApiLogQuery;
import com.lang.portal.upstream.newapi.log.NewApiLogRecord;
import com.lang.portal.upstream.newapi.log.NewApiLogResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 正常响应必须显式携带两个可空增强字段，前端才能区分“未采集”与“旧版本省略”。 */
class RequestLogEnhancementSerializationTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final NewApiLogClient logClient = mock(NewApiLogClient.class);
  private final PortalCommonProperties properties = new PortalCommonProperties();

  private MockHttpServletRequest validRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new jakarta.servlet.http.Cookie("LANG_SESSION", "upstream-session"),
        new jakarta.servlet.http.Cookie("LANG_UID", "42"));
    return validated(request);
  }

  @Test
  void serializesBothEnhancementFieldsAsExplicitNull() throws Exception {
    NewApiLogRecord record =
        new NewApiLogRecord(
            Instant.parse("2026-09-10T10:00:00Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            true,
            500000L,
            "req-1",
            9L);
    when(logClient.listSuccess(eq(SESSION), any(NewApiLogQuery.class)))
        .thenReturn(new NewApiLogPage(1, List.of(record)));

    var response =
        new RequestLogController(new RequestLogQueryService(logClient, properties), properties, FIXED)
            .list(Map.of(), new com.lang.portal.base.security.PortalAuthenticatedUser(
                42L, "ordinary", "Ordinary", "ordinary@example.test"), validRequest());

    ApiResponse<PageData<RequestLogDto>> body = response.getBody();
    // 直接断言 JSON 文本，避免依赖 PageData 的内部结构
    String json = MAPPER.writeValueAsString(body);
    assertThat(json).contains("\"protocol\":null").contains("\"firstTokenLatencyMs\":null");
    assertThat(json).contains("\"amount\":\"1.0\"").contains("\"currency\":\"USD\"");
    assertThat(json).contains("\"total\":1").contains("\"page\":1").contains("\"pageSize\":20");
  }

  /** 标记该请求已通过会话校验；上游凭证只能来自这里，不再由浏览器 Cookie 提供。 */
  private static MockHttpServletRequest validated(MockHttpServletRequest request) {
    request.setAttribute(
        PortalSessionAuthenticationFilter.UPSTREAM_SESSION_ATTRIBUTE, new NewApiSession("upstream-session", 42L));
    return request;
  }

}