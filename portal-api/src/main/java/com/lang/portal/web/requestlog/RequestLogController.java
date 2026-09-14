package com.lang.portal.web.requestlog;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.PageData;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.base.security.ProtectedEndpoint;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.web.usage.UsageTimeRange;
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
@RequestMapping("/portal/api/request-logs")
public class RequestLogController {

  private static final Set<String> LIST_PARAMS =
      Set.of("page", "pageSize", "result", "keyName", "model", "startTime", "endTime");

  private final RequestLogQueryService queryService;
  private final PortalCommonProperties properties;
  private final Clock clock;

  @Autowired
  public RequestLogController(
      RequestLogQueryService queryService, PortalCommonProperties properties) {    this(queryService, properties, Clock.systemUTC());
  }

  RequestLogController(
      RequestLogQueryService queryService, PortalCommonProperties properties, Clock clock) {
    this.queryService = queryService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageData<RequestLogDto>>> list(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    for (String key : params.keySet()) {
      if (!LIST_PARAMS.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
    int page = parsePage(params.get("page"));
    int pageSize = parsePageSize(params.get("pageSize"));
    String result = parseResult(params.get("result"));
    String keyName = bounded(params.get("keyName"), 64, "keyName");
    String model = bounded(params.get("model"), 128, "model");
    String startTime = trimToNull(params.get("startTime"));
    String endTime = trimToNull(params.get("endTime"));
    try {
      UsageTimeRange.resolve(startTime, endTime, clock);
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求时间范围不合法");
    }
    NewApiSession session = session(request);
    return ResponseEntity.ok(
        ApiResponses.ok(
            request,
            queryService.list(
                session, page, pageSize, result, keyName, model, startTime, endTime, clock)));
  }

  private int parsePage(String raw) {
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value >= 1) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 page 不合法");
  }

  private int parsePageSize(String raw) {
    if (raw == null || raw.isBlank()) {
      return properties.portal().usage().defaultPageSize();
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value >= 1 && value <= properties.portal().usage().maxPageSize()) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 pageSize 不合法");
  }

  private String parseResult(String raw) {
    if (raw == null || raw.isBlank()) {
      return "SUCCESS";
    }
    String value = raw.trim();
    if ("SUCCESS".equals(value) || "ERROR".equals(value)) {
      return value;
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 result 不合法");
  }

  private String bounded(String raw, int max, String name) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return null;
    }
    if (value.length() > max || hasControl(value)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + name + " 不合法");
    }
    return value;
  }

  private String trimToNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private boolean hasControl(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        return true;
      }
    }
    return false;
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
