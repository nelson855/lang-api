package com.lang.portal.web.account;

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
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ProtectedEndpoint
@RequestMapping("/portal/api/account")
public class AccountController {

  private final AccountQueryService queryService;
  private final PortalCommonProperties properties;

  public AccountController(AccountQueryService queryService, PortalCommonProperties properties) {
    this.queryService = queryService;
    this.properties = properties;
  }

  @GetMapping("/balance")
  public ResponseEntity<ApiResponse<AccountBalanceDto>> balance(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    if (!params.isEmpty()) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "余额接口不接受查询参数");
    }
    NewApiSession session = session(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, queryService.balance(session)));
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
