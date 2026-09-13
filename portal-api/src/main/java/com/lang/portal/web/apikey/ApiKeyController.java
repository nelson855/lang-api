package com.lang.portal.web.apikey;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.PageData;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.base.security.ProtectedEndpoint;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.web.auth.AuthCsrfService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ProtectedEndpoint
@RequestMapping("/portal/api/api-keys")
public class ApiKeyController {

  private static final Set<String> LIST_PARAMS = Set.of("page", "pageSize", "name", "status");

  private final ApiKeyApplicationService service;
  private final ApiKeyQueryService queryService;
  private final AuthCsrfService csrfService;
  private final PortalCommonProperties properties;

  public ApiKeyController(
      ApiKeyApplicationService service,
      ApiKeyQueryService queryService,
      AuthCsrfService csrfService,
      PortalCommonProperties properties) {
    this.service = service;
    this.queryService = queryService;
    this.csrfService = csrfService;
    this.properties = properties;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageData<ApiKeyDto>>> list(
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
    String name = name(params.get("name"));
    NewApiSession session = session(request);
    return ResponseEntity.ok(ApiResponses.ok(request, queryService.list(session, page, pageSize, name, params.get("status"))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ApiKeyDto>> detail(
      @PathVariable String id,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    return ResponseEntity.ok(ApiResponses.ok(request, queryService.get(session(request), parseId(id))));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ApiKeyCreateResponse>> create(
      @RequestBody CreateApiKeyRequest body,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    csrfService.requireValid(request);
    return ResponseEntity.ok(ApiResponses.ok(request, service.create(session(request), body)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ApiKeyUpdateResponse>> update(
      @PathVariable String id,
      @RequestBody UpdateApiKeyRequest body,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    csrfService.requireValid(request);
    return ResponseEntity.ok(ApiResponses.ok(request, service.update(session(request), parseId(id), body)));
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<ApiResponse<ApiKeyStatusResponse>> status(
      @PathVariable String id,
      @RequestBody ApiKeyStatusRequest body,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    csrfService.requireValid(request);
    if (body == null || body.enabled() == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 enabled 不合法");
    }
    return ResponseEntity.ok(ApiResponses.ok(request, service.updateStatus(session(request), parseId(id), body.enabled())));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<ApiKeyDeleteResponse>> delete(
      @PathVariable String id,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    csrfService.requireValid(request);
    return ResponseEntity.ok(ApiResponses.ok(request, service.delete(session(request), parseId(id))));
  }

  @PostMapping("/{id}/reveal")
  public ResponseEntity<ApiResponse<ApiKeyRevealResponse>> reveal(
      @PathVariable String id,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    csrfService.requireValid(request);
    ApiKeyRevealResponse body = service.reveal(session(request), parseId(id));
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, body));
  }

  private long parseId(String id) {
    try {
      long value = Long.parseLong(id == null ? "" : id.trim());
      if (value > 0) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.NOT_FOUND);
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
      return properties.apiKey().defaultPageSize();
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value >= 1 && value <= properties.apiKey().maxPageSize()) {
        return value;
      }
    } catch (NumberFormatException ignored) {
    }
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 pageSize 不合法");
  }

  private String name(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.length() > 100 || hasControl(value)) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 name 不合法");
    }
    return value;
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
