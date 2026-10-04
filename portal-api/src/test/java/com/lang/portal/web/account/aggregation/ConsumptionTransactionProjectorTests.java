package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionTransactionProjectorTests {

  private static final long USER = 42L;

  private static AggregationLogRecord rec(Instant at, String requestId, String model, long quota) {
    return new AggregationLogRecord(
        at, AggregationLogResult.SUCCESS, 1L, model, requestId, "key", 0L, 0L, 0L, false, quota);
  }

  @Test
  void projectsConsumptionRecordWithStableFields() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(rec(Instant.parse("2026-09-01T12:00:00Z"), "req-1", "gpt-4o", 60L)));
    List<AccountTransactionItemDto> items =
        ConsumptionTransactionProjector.project(snapshot, USER);
    assertThat(items).hasSize(1);
    AccountTransactionItemDto item = items.get(0);
    assertThat(item.transactionId()).startsWith("CONSUMPTION_");
    assertThat(item.occurredAt()).isEqualTo("2026-09-01T12:00:00Z");
    assertThat(item.type()).isEqualTo(AccountTransactionType.CONSUMPTION);
    assertThat(item.direction()).isEqualTo(AccountTransactionDirection.DEBIT);
    assertThat(item.amount()).isEqualTo("60");
    assertThat(item.unit()).isEqualTo(AccountTransactionUnit.QUOTA);
    assertThat(item.currency()).isNull();
    assertThat(item.status()).isEqualTo(AccountTransactionStatus.SUCCEEDED);
    assertThat(item.remark()).isEqualTo("gpt-4o");
    assertThat(item.referenceId()).isEqualTo("req-1");
  }

  @Test
  void blankModelBecomesNullRemark() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(rec(Instant.parse("2026-09-01T12:00:00Z"), "r", null, 1L)));
    AccountTransactionItemDto item = ConsumptionTransactionProjector.project(snapshot, USER).get(0);
    assertThat(item.remark()).isNull();
  }

  @Test
  void duplicateRequestIdAcrossRecordsFailsEntirely() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "dup", "m", 1L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), "dup", "m", 2L)));
    assertThatThrownBy(() -> ConsumptionTransactionProjector.project(snapshot, USER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionId");
  }

  @Test
  void missingRequestIdRecordsAreSkippedFromItems() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", 1L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), null, "m", 2L)));
    List<AccountTransactionItemDto> items =
        ConsumptionTransactionProjector.project(snapshot, USER);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).referenceId()).isEqualTo("r1");
  }

  @Test
  void idsAreStableAcrossRuns() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", 1L)));
    List<AccountTransactionItemDto> a = ConsumptionTransactionProjector.project(snapshot, USER);
    List<AccountTransactionItemDto> b = ConsumptionTransactionProjector.project(snapshot, USER);
    assertThat(a.get(0).transactionId()).isEqualTo(b.get(0).transactionId());
  }
}
