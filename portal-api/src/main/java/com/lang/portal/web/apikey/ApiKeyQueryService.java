package com.lang.portal.web.apikey;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.PageData;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import com.lang.portal.upstream.newapi.token.NewApiTokenClient;
import com.lang.portal.upstream.newapi.token.NewApiTokenPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyQueryService {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyQueryService.class);
  private static final Set<String> STATUSES = Set.of("enabled", "disabled", "expired", "exhausted");

  private final NewApiTokenClient client;
  private final PortalCommonProperties properties;

  public ApiKeyQueryService(NewApiTokenClient client, PortalCommonProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  public PageData<ApiKeyDto> list(NewApiSession session, int page, int pageSize, String name, String status) {
    int max = properties.apiKey().maxPageSize();
    if (page < 1) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 page 不合法");
    }
    if (pageSize < 1 || pageSize > max) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 pageSize 不合法");
    }
    String filter = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    if (!filter.isEmpty() && !STATUSES.contains(filter)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 status 不合法");
    }
    String keyword = name == null ? "" : name.trim();
    Instant now = Instant.now();
    if (filter.isEmpty()) {
      NewApiTokenPage result = keyword.isEmpty()
          ? client.listTokens(session, page, pageSize)
          : client.searchTokens(session, keyword, page, pageSize);
      return map(result, page, pageSize, now);
    }
    return aggregate(session, keyword, page, pageSize, filter, now);
  }

  public ApiKeyDto get(NewApiSession session, long id) {
    if (id <= 0) {
      throw new PortalException(PortalErrorCode.NOT_FOUND);
    }
    return ApiKeyDto.from(client.getToken(session, id), Instant.now());
  }

  private PageData<ApiKeyDto> map(NewApiTokenPage result, int page, int pageSize, Instant now) {
    List<ApiKeyDto> items = new ArrayList<>();
    for (NewApiToken token : result.items()) {
      items.add(ApiKeyDto.from(token, now));
    }
    return PageData.of(items, page, pageSize, result.total());
  }

  private PageData<ApiKeyDto> aggregate(
      NewApiSession session, String keyword, int page, int pageSize, String status, Instant now) {
    int upstreamSize = properties.apiKey().maxPageSize();
    int budget = Math.max(1, properties.apiKey().statusAggregationMaxPages());
    LinkedHashMap<Long, NewApiToken> seen = new LinkedHashMap<>();
    int firstTotal = -1;
    int fetched = 0;
    int upstreamPage = 1;
    while (true) {
      NewApiTokenPage result = keyword.isEmpty()
          ? client.listTokens(session, upstreamPage, upstreamSize)
          : client.searchTokens(session, keyword, upstreamPage, upstreamSize);
      fetched++;
      boolean drifted = firstTotal >= 0 && result.total() != firstTotal;
      if (firstTotal < 0) {
        firstTotal = result.total();
      }
      for (NewApiToken token : result.items()) {
        if (token != null && token.id() != null) {
          seen.putIfAbsent(token.id(), token);
        }
      }
      boolean lastPage = result.items().size() < upstreamSize;
      boolean covered = seen.size() >= firstTotal;
      if (drifted || lastPage || covered) {
        break;
      }
      if (fetched >= budget) {
        throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
      }
      upstreamPage++;
    }
    log.info("event=apikey_aggregate pages={} unique={} total={}", fetched, seen.size(), firstTotal);
    List<ApiKeyDto> filtered = new ArrayList<>();
    for (NewApiToken token : seen.values()) {
      ApiKeyDto dto = ApiKeyDto.from(token, now);
      if (dto.status().equals(status)) {
        filtered.add(dto);
      }
    }
    int from = Math.min((page - 1) * pageSize, filtered.size());
    int to = Math.min(from + pageSize, filtered.size());
    return PageData.of(filtered.subList(from, to), page, pageSize, filtered.size());
  }
}
