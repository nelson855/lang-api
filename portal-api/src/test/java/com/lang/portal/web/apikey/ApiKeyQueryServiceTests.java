package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.base.response.PageData;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.NewApiTokenPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApiKeyQueryServiceTests {

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  private final NewApiTokenClient client = mock(NewApiTokenClient.class);

  private ApiKeyQueryService service(PortalCommonProperties props) {
    return new ApiKeyQueryService(client, props);
  }

  private ApiKeyQueryService service() {
    return service(new PortalCommonProperties());
  }

  private static NewApiToken token(long id, String name, int status, long remain, long expired) {
    return new NewApiToken(
        id, 42L, "fN95**********CMHQ", status, name, 1789180807L, 1789180807L,
        expired, remain, false, false, "", "", 0L, "", false);
  }

  private static NewApiTokenPage page(int page, int pageSize, int total, NewApiToken... items) {
    return new NewApiTokenPage(page, pageSize, total, List.of(items));
  }

  @Test
  void defaultListPassesThroughPreservingOrderAndMask() {
    when(client.listTokens(SESSION, 1, 20))
        .thenReturn(page(1, 20, 2, token(9, "second", 1, 100L, -1L), token(7, "first", 2, 100L, -1L)));

    PageData<ApiKeyDto> result = service().list(SESSION, 1, 20, null, null);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).extracting(ApiKeyDto::id).containsExactly(9L, 7L);
    assertThat(result.items()).extracting(ApiKeyDto::maskedKey)
        .allSatisfy(mask -> assertThat(mask).startsWith("sk-").contains("**********"));
    assertThat(result.items()).extracting(ApiKeyDto::status).containsExactly("enabled", "disabled");
    verify(client, times(1)).listTokens(SESSION, 1, 20);
  }

  @Test
  void invalidPageAndStatusRejectedBeforeUpstream() {
    ApiKeyQueryService service = service();
    assertThatThrownBy(() -> service.list(SESSION, 0, 20, null, null))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> service.list(SESSION, 1, 101, null, null))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    assertThatThrownBy(() -> service.list(SESSION, 1, 20, null, "bogus"))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
    verify(client, never()).listTokens(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void nameSearchRoutesToSearchAndBlankFallsBackToList() {
    when(client.searchTokens(SESSION, "probe", 1, 20))
        .thenReturn(page(1, 20, 1, token(7, "probe", 1, 100L, -1L)));
    when(client.listTokens(SESSION, 1, 20)).thenReturn(page(1, 20, 0));

    PageData<ApiKeyDto> found = service().list(SESSION, 1, 20, "probe", null);
    assertThat(found.total()).isEqualTo(1);
    verify(client, times(1)).searchTokens(SESSION, "probe", 1, 20);

    PageData<ApiKeyDto> empty = service().list(SESSION, 1, 20, "   ", null);
    assertThat(empty.total()).isEqualTo(0);
    assertThat(empty.items()).isEmpty();
    verify(client, times(1)).listTokens(SESSION, 1, 20);
    verify(client, never()).searchTokens(eq(SESSION), eq("   "), eq(1), eq(20));
  }

  @Test
  void searchKeywordNeverLogged() {
    ch.qos.logback.classic.Logger queryLogger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ApiKeyQueryService.class);
    ch.qos.logback.classic.Level previous = queryLogger.getLevel();
    queryLogger.setLevel(ch.qos.logback.classic.Level.INFO);
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    ((Logger) LoggerFactory.getLogger(ApiKeyQueryService.class)).addAppender(events);
    try {
      when(client.searchTokens(SESSION, "secret-xyz-123", 1, 20)).thenReturn(page(1, 20, 0));
      service().list(SESSION, 1, 20, "secret-xyz-123", null);
      String output = events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(output).doesNotContain("secret-xyz-123");
    } finally {
      ((Logger) LoggerFactory.getLogger(ApiKeyQueryService.class)).detachAppender(events);
      queryLogger.setLevel(previous);
    }
  }

  private PortalCommonProperties aggregationProps(int maxPages) {
    PortalCommonProperties props = new PortalCommonProperties();
    props.apiKey().setDefaultPageSize(2);
    props.apiKey().setMaxPageSize(2);
    props.apiKey().setStatusAggregationMaxPages(maxPages);
    return props;
  }

  @Test
  void statusFilterAggregatesAcrossPagesNotJustCurrentPage() {
    when(client.listTokens(SESSION, 1, 2))
        .thenReturn(page(1, 2, 3, token(7, "a", 1, 100L, -1L), token(8, "b", 2, 100L, -1L)));
    when(client.listTokens(SESSION, 2, 2))
        .thenReturn(page(2, 2, 3, token(8, "b", 2, 100L, -1L), token(9, "c", 1, 100L, -1L)));

    PageData<ApiKeyDto> result = service(aggregationProps(5)).list(SESSION, 1, 2, null, "enabled");

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).extracting(ApiKeyDto::id).containsExactly(7L, 9L);
    verify(client, times(1)).listTokens(SESSION, 1, 2);
    verify(client, times(1)).listTokens(SESSION, 2, 2);
  }

  @Test
  void statusPagingAppliesAfterFiltering() {
    when(client.listTokens(SESSION, 1, 2))
        .thenReturn(page(1, 2, 3, token(7, "a", 1, 100L, -1L), token(8, "b", 2, 100L, -1L)));
    when(client.listTokens(SESSION, 2, 2))
        .thenReturn(page(2, 2, 3, token(8, "b", 2, 100L, -1L), token(9, "c", 1, 100L, -1L)));

    PageData<ApiKeyDto> result = service(aggregationProps(5)).list(SESSION, 2, 1, null, "enabled");

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).extracting(ApiKeyDto::id).containsExactly(9L);
  }

  @Test
  void totalDriftContinuesOnceWithStableSnapshot() {
    when(client.listTokens(SESSION, 1, 2))
        .thenReturn(page(1, 2, 3, token(7, "a", 1, 100L, -1L), token(8, "b", 2, 100L, -1L)));
    when(client.listTokens(SESSION, 2, 2))
        .thenReturn(page(2, 2, 9, token(8, "b", 2, 100L, -1L), token(9, "c", 1, 100L, -1L)));

    PageData<ApiKeyDto> result = service(aggregationProps(5)).list(SESSION, 1, 2, null, "enabled");

    assertThat(result.total()).isEqualTo(2);
    verify(client, times(1)).listTokens(SESSION, 1, 2);
    verify(client, times(1)).listTokens(SESSION, 2, 2);
    verify(client, never()).listTokens(eq(SESSION), eq(3), eq(2));
  }

  @Test
  void budgetExhaustedFailsInsteadOfPartial() {
    when(client.listTokens(SESSION, 1, 2))
        .thenReturn(page(1, 2, 5, token(7, "a", 1, 100L, -1L), token(8, "b", 2, 100L, -1L)));

    assertThatThrownBy(() -> service(aggregationProps(1)).list(SESSION, 1, 2, null, "enabled"))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
    verify(client, times(1)).listTokens(SESSION, 1, 2);
    verify(client, never()).listTokens(eq(SESSION), eq(2), eq(2));
  }

  @Test
  void emptyAggregationReturnsEmptyPage() {
    when(client.searchTokens(SESSION, "nope", 1, 2)).thenReturn(page(1, 2, 0));

    PageData<ApiKeyDto> result = service(aggregationProps(5)).list(SESSION, 1, 2, "nope", "enabled");

    assertThat(result.total()).isEqualTo(0);
    assertThat(result.items()).isEmpty();
    verify(client, times(1)).searchTokens(SESSION, "nope", 1, 2);
  }

  @Test
  void detailReturnsDtoAndMissingBecomesNotFound() {
    when(client.getToken(SESSION, 7L)).thenReturn(token(7, "probe", 1, 100L, -1L));

    ApiKeyDto dto = service().get(SESSION, 7L);
    assertThat(dto.id()).isEqualTo(7L);
    assertThat(dto.maskedKey()).startsWith("sk-");
    verify(client, times(1)).getToken(SESSION, 7L);

    when(client.getToken(SESSION, 999L)).thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> service().get(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void upstreamFailurePropagatesWithoutLeak() {
    when(client.listTokens(SESSION, 1, 20)).thenThrow(new UpstreamException(PortalErrorCode.UPSTREAM_ERROR));
    assertThatThrownBy(() -> service().list(SESSION, 1, 20, null, null))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }
}
