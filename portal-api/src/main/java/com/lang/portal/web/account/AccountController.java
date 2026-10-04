package com.lang.portal.web.account;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.PageData;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.base.security.PortalUpstreamSessions;
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
    NewApiSession session = PortalUpstreamSessions.require(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, queryService.balance(session)));
  }

  @GetMapping("/topup-options")
  public ResponseEntity<ApiResponse<TopupCapability>> topupOptions(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    if (!params.isEmpty()) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "充值能力接口不接受查询参数");
    }
    NewApiSession session = PortalUpstreamSessions.require(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, queryService.topupOptions(session)));
  }

  @GetMapping("/topups")
  public ResponseEntity<ApiResponse<PageData<TopupRecord>>> topups(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    TopupPageQuery query =
        TopupPageQuery.resolve(
            params,
            properties.portal().usage().defaultPageSize(),
            properties.portal().usage().maxPageSize());
    NewApiSession session = PortalUpstreamSessions.require(request);
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(ApiResponses.ok(request, queryService.topups(session, query.page(), query.pageSize())));
  }

}
