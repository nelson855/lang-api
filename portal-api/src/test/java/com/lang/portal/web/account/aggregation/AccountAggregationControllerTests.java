package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.security.PortalAuthenticatedUser;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountAggregationControllerTests {

  private static final PortalAuthenticatedUser USER =
      new PortalAuthenticatedUser(42L, "ordinary", "Ordinary", "o@e.test");
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  private StubConsumptionSnapshotService snapshotService;
  private PortalCommonProperties properties;

  private AccountAggregationController controller() {
    properties = new PortalCommonProperties();
    snapshotService = new StubConsumptionSnapshotService();
    return new AccountAggregationController(snapshotService, properties, FIXED);
  }

  private MockHttpServletRequest authed() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("LANG_SESSION", "sess"), new Cookie("LANG_UID", "42"));
    return request;
  }

  private Map<String, String> validSummaryParams() {
    Map<String, String> m = new HashMap<>();
    m.put("startTime", "2026-09-01T00:00:00Z");
    m.put("endTime", "2026-09-02T00:00:00Z");
    m.put("granularity", "HOUR");
    m.put("timezone", "UTC");
    return m;
  }

  private Map<String, String> validTransactionParams() {
    Map<String, String> m = validSummaryParams();
    m.put("page", "1");
    m.put("pageSize", "20");
    return m;
  }

  @Test
  void summaryRejectsMissingParameters() {
    Map<String, String> onlyStart = new HashMap<>();
    onlyStart.put("startTime", "2026-09-01T00:00:00Z");
    assertThatThrownBy(() -> controller().consumptionSummary(onlyStart, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void summaryRejectsUnknownParam() {
    Map<String, String> p = validSummaryParams();
    p.put("userId", "42");
    assertThatThrownBy(() -> controller().consumptionSummary(p, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void summaryRejectsBaselineOverride() {
    Map<String, String> p = validSummaryParams();
    p.put("baselineVersion", "p1");
    assertThatThrownBy(() -> controller().consumptionSummary(p, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void summaryRejectsIllegalGranularityAndTimezone() {
    Map<String, String> p = validSummaryParams();
    p.put("granularity", "MINUTE");
    assertThatThrownBy(() -> controller().consumptionSummary(p, USER, authed()))
        .isInstanceOf(PortalException.class);

    Map<String, String> p2 = validSummaryParams();
    p2.put("timezone", "Not/AZone");
    assertThatThrownBy(() -> controller().consumptionSummary(p2, USER, authed()))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void summaryRejectsRangeExceeding7Days() {
    Map<String, String> p = validSummaryParams();
    p.put("startTime", "2026-08-01T00:00:00Z");
    p.put("endTime", "2026-09-02T00:00:00Z");
    assertThatThrownBy(() -> controller().consumptionSummary(p, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void summaryRejectsUnauthenticated() {
    MockHttpServletRequest noCookie = new MockHttpServletRequest();
    assertThatThrownBy(() -> controller().consumptionSummary(validSummaryParams(), null, noCookie))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void summaryRejectsMismatchedPrincipalAndCookie() {
    PortalAuthenticatedUser other =
        new PortalAuthenticatedUser(77L, "x", "X", "x@x.test");
    assertThatThrownBy(() -> controller().consumptionSummary(validSummaryParams(), other, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void summaryRejectsDuplicatedSessionCookie() {
    MockHttpServletRequest dup = new MockHttpServletRequest();
    dup.setCookies(
        new Cookie("LANG_SESSION", "a"),
        new Cookie("LANG_SESSION", "b"),
        new Cookie("LANG_UID", "42"));
    assertThatThrownBy(() -> controller().consumptionSummary(validSummaryParams(), USER, dup))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UNAUTHENTICATED);
  }

  @Test
  void summaryReturnsWrappedDataWithBaselineAndRange() {
    ResponseEntity<ApiResponse<AccountConsumptionSummaryData>> response =
        controller().consumptionSummary(validSummaryParams(), USER, authed());
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    AccountConsumptionSummaryData data = response.getBody().data();
    assertThat(data.baselineVersion()).isEqualTo("p2-2026-09-22-a");
    assertThat(data.range().start()).isEqualTo("2026-09-01T00:00:00Z");
    assertThat(data.range().granularity()).isEqualTo("HOUR");
    assertThat(data.range().timezone()).isEqualTo("UTC");
    assertThat(data.recordCount().availability()).isEqualTo(AccountAvailability.AVAILABLE);
    assertThat(data.moneyTotal().availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.moneyTotal().reasonCode())
        .isEqualTo(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
  }

  @Test
  void transactionsRejectsIllegalType() {
    Map<String, String> p = validTransactionParams();
    p.put("type", "BONUS");
    assertThatThrownBy(() -> controller().transactions(p, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void transactionsRejectsDeepPaginationBeyondMaxRecords() {
    Map<String, String> p = validTransactionParams();
    p.put("page", "11");
    p.put("pageSize", "20");
    assertThatThrownBy(() -> controller().transactions(p, USER, authed()))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void transactionsRejectsIllegalPageSize() {
    Map<String, String> p = validTransactionParams();
    p.put("pageSize", "0");
    assertThatThrownBy(() -> controller().transactions(p, USER, authed()))
        .isInstanceOf(PortalException.class);
    Map<String, String> p2 = validTransactionParams();
    p2.put("pageSize", "101");
    assertThatThrownBy(() -> controller().transactions(p2, USER, authed()))
        .isInstanceOf(PortalException.class);
    Map<String, String> p3 = validTransactionParams();
    p3.put("page", "abc");
    assertThatThrownBy(() -> controller().transactions(p3, USER, authed()))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void transactionsTopupReturnsUnavailableWithoutTouchingConsumptionSource() {
    Map<String, String> p = validTransactionParams();
    p.put("type", "TOPUP");
    AccountAggregationController controller = controller();
    ResponseEntity<ApiResponse<AccountTransactionsData>> response =
        controller.transactions(p, USER, authed());
    AccountTransactionsData data = response.getBody().data();
    assertThat(data.items()).isEmpty();
    assertThat(data.total()).isZero();
    assertThat(data.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.reasonCode()).isEqualTo(AccountReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(snapshotService.snapshotCalls()).isZero();
  }

  @Test
  void transactionsRefundReturnsUnavailable() {
    Map<String, String> p = validTransactionParams();
    p.put("type", "REFUND");
    ResponseEntity<ApiResponse<AccountTransactionsData>> response =
        controller().transactions(p, USER, authed());
    AccountTransactionsData data = response.getBody().data();
    assertThat(data.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.reasonCode()).isEqualTo(AccountReasonCode.SOURCE_NOT_AVAILABLE);
  }

  @Test
  void transactionsDefaultAllTypesReturnsPartialCoverage() {
    Map<String, String> p = validTransactionParams();
    p.remove("type");
    ResponseEntity<ApiResponse<AccountTransactionsData>> response =
        controller().transactions(p, USER, authed());
    AccountTransactionsData data = response.getBody().data();
    assertThat(data.type()).isNull();
    assertThat(data.availability()).isEqualTo(AccountAvailability.PARTIAL);
    assertThat(data.coverage()).hasSize(3);
  }
}
