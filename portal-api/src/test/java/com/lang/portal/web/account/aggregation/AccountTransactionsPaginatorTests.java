package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationGranularity;
import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.base.exception.PortalException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountTransactionsPaginatorTests {

  private static final long USER = 7L;

  private static AggregationLogRecord rec(Instant at, String requestId, long quota) {
    return new AggregationLogRecord(
        at, AggregationLogResult.SUCCESS, null, "m", requestId, null, 0L, 0L, 0L, false, quota);
  }

  private static AggregationQueryContext ctx() {
    return AggregationQueryContext.of(
        "2026-09-01T00:00:00Z",
        "2026-09-02T00:00:00Z",
        "UTC",
        AggregationGranularity.HOUR,
        "p2-2026-09-22-a",
        new PortalCommonProperties().aggregation());
  }

  private static ConsumptionSnapshot sampleSnapshot() {
    return ConsumptionSnapshot.from(
        List.of(
            rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", 10L),
            rec(Instant.parse("2026-09-01T11:00:00Z"), "r2", 20L),
            rec(Instant.parse("2026-09-01T12:00:00Z"), "r3", 30L),
            rec(Instant.parse("2026-09-01T13:00:00Z"), "r4", 40L)));
  }

  @Test
  void sortsByOccurredAtDescThenTransactionIdAsc() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 1, 10, 200);
    assertThat(data.total()).isEqualTo(4);
    assertThat(data.items()).hasSize(4);
    assertThat(data.items().get(0).occurredAt()).isEqualTo("2026-09-01T13:00:00Z");
    assertThat(data.items().get(3).occurredAt()).isEqualTo("2026-09-01T10:00:00Z");
  }

  @Test
  void sameTimestampUsesTransactionIdAsc() {
    // 两条同时刻记录：排序必须按 transactionId 字典序稳定
    Instant same = Instant.parse("2026-09-01T10:00:00Z");
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(rec(same, "zzz-request", 1L), rec(same, "aaa-request", 2L)));
    AccountTransactionsData first =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 1, 10, 200);
    AccountTransactionsData second =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 1, 10, 200);
    assertThat(first.items().get(0).transactionId()).isEqualTo(second.items().get(0).transactionId());
    assertThat(first.items().get(1).transactionId()).isEqualTo(second.items().get(1).transactionId());
    assertThat(first.items().get(0).transactionId())
        .isLessThan(first.items().get(1).transactionId());
  }

  @Test
  void adjacentPagesAreStableAndComplete() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData page1 =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 1, 2, 200);
    AccountTransactionsData page2 =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 2, 2, 200);
    assertThat(page1.total()).isEqualTo(4);
    assertThat(page2.total()).isEqualTo(4);
    assertThat(page1.items()).hasSize(2);
    assertThat(page2.items()).hasSize(2);
    List<String> page1Ids = page1.items().stream().map(AccountTransactionItemDto::transactionId).toList();
    List<String> page2Ids = page2.items().stream().map(AccountTransactionItemDto::transactionId).toList();
    assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
    // 相邻页时间递减
    assertThat(page1.items().get(1).occurredAt())
        .isGreaterThanOrEqualTo(page2.items().get(0).occurredAt());
  }

  @Test
  void emptyPageBeyondTotalReturnsEmptyItems() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 3, 2, 200);
    assertThat(data.total()).isEqualTo(4);
    assertThat(data.items()).isEmpty();
  }

  @Test
  void deepPaginationBeyondMaxRecordsIsRejected() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    // page=11, pageSize=20 → endOffset = 220 > 200
    assertThatThrownBy(
            () ->
                AccountTransactionsPaginator.paginate(
                    snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 11, 20, 200))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void typeNullReturnsAllSourcesCoverage() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(snapshot, ctx(), USER, null, 1, 20, 200);
    assertThat(data.type()).isNull();
    assertThat(data.coverage()).hasSize(3);
    assertThat(data.coverage().get(0).type()).isEqualTo(AccountTransactionType.TOPUP);
    assertThat(data.coverage().get(0).availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.coverage().get(0).reasonCode()).isEqualTo(AccountReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.coverage().get(1).type()).isEqualTo(AccountTransactionType.CONSUMPTION);
    assertThat(data.coverage().get(1).availability()).isEqualTo(AccountAvailability.AVAILABLE);
    assertThat(data.coverage().get(2).type()).isEqualTo(AccountTransactionType.REFUND);
    assertThat(data.coverage().get(2).availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.coverage().get(2).reasonCode()).isEqualTo(AccountReasonCode.SOURCE_NOT_AVAILABLE);
    assertThat(data.availability()).isEqualTo(AccountAvailability.PARTIAL);
  }

  @Test
  void onlyTopupReturnsEmptyItemsWithUnavailableOverall() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.TOPUP, 1, 20, 200);
    assertThat(data.items()).isEmpty();
    assertThat(data.total()).isZero();
    assertThat(data.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.reasonCode()).isEqualTo(AccountReasonCode.BASELINE_NOT_VERIFIED);
    assertThat(data.coverage()).hasSize(1);
    assertThat(data.coverage().get(0).type()).isEqualTo(AccountTransactionType.TOPUP);
  }

  @Test
  void onlyRefundReturnsEmptyItemsWithSourceNotAvailable() {
    ConsumptionSnapshot snapshot = sampleSnapshot();
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.REFUND, 1, 20, 200);
    assertThat(data.items()).isEmpty();
    assertThat(data.total()).isZero();
    assertThat(data.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(data.reasonCode()).isEqualTo(AccountReasonCode.SOURCE_NOT_AVAILABLE);
    assertThat(data.coverage()).hasSize(1);
    assertThat(data.coverage().get(0).type()).isEqualTo(AccountTransactionType.REFUND);
  }

  @Test
  void partialConsumptionMarkedWhenRequestIdMissing() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", 1L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), null, 2L)));
    AccountTransactionsData data =
        AccountTransactionsPaginator.paginate(
            snapshot, ctx(), USER, AccountTransactionType.CONSUMPTION, 1, 20, 200);
    assertThat(data.availability()).isEqualTo(AccountAvailability.PARTIAL);
    assertThat(data.reasonCode()).isEqualTo(AccountReasonCode.MISSING_STABLE_REFERENCE);
    assertThat(data.coverage().get(0).availability()).isEqualTo(AccountAvailability.PARTIAL);
    assertThat(data.total()).isEqualTo(1);
  }
}
