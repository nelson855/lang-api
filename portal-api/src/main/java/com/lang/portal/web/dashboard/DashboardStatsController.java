package com.lang.portal.web.dashboard;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.base.security.ProtectedEndpoint;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ProtectedEndpoint
@RequestMapping("/portal/api/dashboard")
public class DashboardStatsController {

  private static final Set<String> QUERY_PARAMS =
      Set.of("startTime", "endTime", "granularity", "timezone");

  private final DashboardStatsQueryService queryService;
  private final PortalCommonProperties properties;
  private final Clock clock;

  @Autowired
  public DashboardStatsController(
      DashboardStatsQueryService queryService, PortalCommonProperties properties) {
    this(queryService, properties, Clock.systemUTC());
  }

  DashboardStatsController(
      DashboardStatsQueryService queryService, PortalCommonProperties properties, Clock clock) {
    this.queryService = queryService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<DashboardStatsData>> stats(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    for (String key : params.keySet()) {
      if (!QUERY_PARAMS.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
    String startTime = trimToNull(params.get("startTime"));
    String endTime = trimToNull(params.get("endTime"));
    String granularity = trimToNull(params.get("granularity"));
    String timezone = trimToNull(params.get("timezone"));
    if (startTime == null || endTime == null || granularity == null || timezone == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "四个查询参数均为必填");
    }
    NewApiSession session = session(request);
    if (user != null && user.id() != session.userId()) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    String userId = user != null ? Long.toString(user.id()) : Long.toString(session.userId());
    try {
      AggregationQueryContext.of(
          startTime,
          endTime,
          timezone,
          AggregationGranularity.valueOf(granularity),
          properties.aggregation().baselineVersion(),
          properties.aggregation());
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求时间范围不合法");
    }
    try {
      DashboardStatsData data =
          queryService.query(session, userId, startTime, endTime, timezone, granularity, clock);
      return ResponseEntity.ok(ApiResponses.ok(request, data));
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求时间范围不合法");
    }
  }

  private String trimToNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private NewApiSession session(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    Map<String, String> values = new HashMap<>();
    for (Cookie cookie : cookies) {
      String name = cookie.getName();
      if (!properties.auth().cookie().sessionName().equals(name)
          && !properties.auth().cookie().userIdName().equals(name)) {
        continue;
      }
      if (values.putIfAbsent(name, cookie.getValue()) != null) {
        throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
      }
    }
    String session = values.get(properties.auth().cookie().sessionName());
    String userId = values.get(properties.auth().cookie().userIdName());
    if (session == null || session.isBlank() || userId == null || userId.isBlank()) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
    try {
      long id = Long.parseLong(userId);
      if (id > 0) {
        return new NewApiSession(session, id);
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
  }
}
