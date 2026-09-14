package com.lang.portal.web.usage;

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
@RequestMapping("/portal/api/usage")
public class UsageController {

  private static final Set<String> QUERY_PARAMS = Set.of("startTime", "endTime");

  private final UsageQueryService queryService;
  private final PortalCommonProperties properties;
  private final Clock clock;

  @Autowired
  public UsageController(UsageQueryService queryService, PortalCommonProperties properties) {
    this(queryService, properties, Clock.systemUTC());
  }

  UsageController(UsageQueryService queryService, PortalCommonProperties properties, Clock clock) {
    this.queryService = queryService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping("/summary")
  public ResponseEntity<ApiResponse<UsageSummaryDto>> summary(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    TimePair time = timeParams(params);
    NewApiSession session = session(request);
    return ResponseEntity.ok(
        ApiResponses.ok(
            request, queryService.summary(session, time.start(), time.end(), clock)));
  }

  @GetMapping("/timeseries")
  public ResponseEntity<ApiResponse<UsageTimeseriesDto>> timeseries(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    TimePair time = timeParams(params);
    NewApiSession session = session(request);
    return ResponseEntity.ok(
        ApiResponses.ok(
            request, queryService.timeseries(session, time.start(), time.end(), clock)));
  }

  private TimePair timeParams(Map<String, String> params) {
    for (String key : params.keySet()) {
      if (!QUERY_PARAMS.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
    String startTime = trimToNull(params.get("startTime"));
    String endTime = trimToNull(params.get("endTime"));
    try {
      UsageTimeRange.resolve(startTime, endTime, clock);
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求时间范围不合法");
    }
    return new TimePair(startTime, endTime);
  }

  private String trimToNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private record TimePair(String start, String end) {}

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
