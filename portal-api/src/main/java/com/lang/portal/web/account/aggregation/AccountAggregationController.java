package com.lang.portal.web.account.aggregation;

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
@RequestMapping("/portal/api/account")
public class AccountAggregationController {

  private static final Set<String> SUMMARY_PARAMS =
      Set.of("startTime", "endTime", "granularity", "timezone");
  private static final Set<String> TRANSACTION_PARAMS =
      Set.of("startTime", "endTime", "granularity", "timezone", "page", "pageSize", "type");
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final AccountConsumptionSnapshotService snapshotService;
  private final PortalCommonProperties properties;
  private final Clock clock;

  @Autowired
  public AccountAggregationController(
      AccountConsumptionSnapshotService snapshotService, PortalCommonProperties properties) {
    this(snapshotService, properties, Clock.systemUTC());
  }

  AccountAggregationController(
      AccountConsumptionSnapshotService snapshotService,
      PortalCommonProperties properties,
      Clock clock) {
    this.snapshotService = snapshotService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping("/consumption-summary")
  public ResponseEntity<ApiResponse<AccountConsumptionSummaryData>> consumptionSummary(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    rejectUnknown(params, SUMMARY_PARAMS);
    AggregationQueryContext context = parseContext(params);
    NewApiSession session = session(request);
    requirePrincipalMatchesSession(user, session);
    ConsumptionSnapshot snapshot = snapshotService.loadSnapshot(session, context, clock);
    AccountConsumptionSummaryData data = ConsumptionSummaryCalculator.summarize(snapshot, context);
    return ResponseEntity.ok(ApiResponses.ok(request, data));
  }

  @GetMapping("/transactions")
  public ResponseEntity<ApiResponse<AccountTransactionsData>> transactions(
      @RequestParam Map<String, String> params,
      @AuthenticationPrincipal PortalAuthenticatedUser user,
      HttpServletRequest request) {
    rejectUnknown(params, TRANSACTION_PARAMS);
    AggregationQueryContext context = parseContext(params);
    int page = parsePositiveInt(params.get("page"), 1, "page");
    int pageSize = parsePositiveInt(params.get("pageSize"), DEFAULT_PAGE_SIZE, "pageSize");
    if (pageSize > MAX_PAGE_SIZE) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "pageSize 最大为 " + MAX_PAGE_SIZE);
    }
    AccountTransactionType requestedType = parseType(params.get("type"));
    NewApiSession session = session(request);
    requirePrincipalMatchesSession(user, session);

    int maxRecords = properties.aggregation().maxRecords();
    long endOffset = (long) page * (long) pageSize;
    if (endOffset > maxRecords) {
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "分页窗口超过当前保护上限，请缩小页码或页大小");
    }

    ConsumptionSnapshot snapshot =
        requestedType == null || requestedType == AccountTransactionType.CONSUMPTION
            ? snapshotService.loadSnapshotForTransactions(
                session, context, requestedType, page, pageSize, clock)
            : ConsumptionSnapshot.from(java.util.List.of());
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, context, session.userId(), requestedType, page, pageSize, maxRecords);
    return ResponseEntity.ok(ApiResponses.ok(request, data));
  }

  private static void rejectUnknown(Map<String, String> params, Set<String> allowed) {
    for (String key : params.keySet()) {
      if (!allowed.contains(key)) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + key + " 不合法");
      }
    }
  }

  private AggregationQueryContext parseContext(Map<String, String> params) {
    String startTime = trimToNull(params.get("startTime"));
    String endTime = trimToNull(params.get("endTime"));
    String granularity = trimToNull(params.get("granularity"));
    String timezone = trimToNull(params.get("timezone"));
    if (startTime == null || endTime == null || granularity == null || timezone == null) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "四个查询参数均为必填");
    }
    try {
      return AggregationQueryContext.of(
          startTime,
          endTime,
          timezone,
          AggregationGranularity.valueOf(granularity),
          properties.aggregation().baselineVersion(),
          properties.aggregation());
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "请求时间范围不合法");
    }
  }

  private static int parsePositiveInt(String raw, int defaultValue, String name) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 1) {
        throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, name + " 必须为正整数");
      }
      return value;
    } catch (NumberFormatException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, name + " 必须为整数");
    }
  }

  private static AccountTransactionType parseType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return AccountTransactionType.valueOf(raw.trim());
    } catch (IllegalArgumentException e) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "未知流水类型");
    }
  }

  private static String trimToNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  private static void requirePrincipalMatchesSession(
      PortalAuthenticatedUser user, NewApiSession session) {
    if (user != null && user.id() != session.userId()) {
      throw new PortalException(PortalErrorCode.UNAUTHENTICATED);
    }
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
