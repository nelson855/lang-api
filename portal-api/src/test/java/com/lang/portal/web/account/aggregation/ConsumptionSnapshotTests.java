package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationLogResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionSnapshotTests {

  private static AggregationLogRecord rec(Instant at, String requestId, String model, long quota) {
    return new AggregationLogRecord(
        at, AggregationLogResult.SUCCESS, 1L, model, requestId, "key", 0L, 0L, 0L, false, quota);
  }

  @Test
  void emptyRangeYieldsZeroRecordCountAndZeroQuota() {
    ConsumptionSnapshot snapshot = ConsumptionSnapshot.from(List.of());
    assertThat(snapshot.records()).isEmpty();
    assertThat(snapshot.totalCount()).isZero();
    assertThat(snapshot.missingReferenceCount()).isZero();
    assertThat(snapshot.totalQuota().toString()).isEqualTo("0");
  }

  @Test
  void aggregatesMultipleRecordsExactly() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "gpt-4o", 10L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), "r2", "gpt-4o", 20L),
                rec(Instant.parse("2026-09-01T12:00:00Z"), "r3", "claude", 30L)));
    assertThat(snapshot.totalCount()).isEqualTo(3);
    assertThat(snapshot.totalQuota().toString()).isEqualTo("60");
    assertThat(snapshot.missingReferenceCount()).isZero();
  }

  @Test
  void countsMissingRequestIdWithoutDroppingFromAggregate() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", 10L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), null, "m", 20L),
                rec(Instant.parse("2026-09-01T12:00:00Z"), " ", "m", 30L)));
    assertThat(snapshot.totalCount()).isEqualTo(3);
    assertThat(snapshot.totalQuota().toString()).isEqualTo("60");
    assertThat(snapshot.missingReferenceCount()).isEqualTo(2);
    assertThat(snapshot.projectable()).hasSize(1);
  }

  @Test
  void rejectsNegativeQuotaRecords() {
    assertThatThrownBy(
            () ->
                ConsumptionSnapshot.from(
                    List.of(rec(Instant.parse("2026-09-01T10:00:00Z"), "r", "m", -5L))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quotaOverflowTriggersFailure() {
    assertThatThrownBy(
            () ->
                ConsumptionSnapshot.from(
                    List.of(
                        rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", Long.MAX_VALUE),
                        rec(Instant.parse("2026-09-01T11:00:00Z"), "r2", "m", 10L))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void snapshotIsImmutable() {
    List<AggregationLogRecord> source =
        new java.util.ArrayList<>(
            List.of(rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", 10L)));
    ConsumptionSnapshot snapshot = ConsumptionSnapshot.from(source);
    source.add(rec(Instant.parse("2026-09-01T11:00:00Z"), "r2", "m", 20L));
    assertThat(snapshot.totalCount()).isEqualTo(1);
    assertThatThrownBy(() -> snapshot.records().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void projectableOnlyContainsRecordsWithRequestId() {
    ConsumptionSnapshot snapshot =
        ConsumptionSnapshot.from(
            List.of(
                rec(Instant.parse("2026-09-01T10:00:00Z"), "r1", "m", 10L),
                rec(Instant.parse("2026-09-01T11:00:00Z"), null, "m", 20L)));
    assertThat(snapshot.projectable()).hasSize(1);
    assertThat(snapshot.projectable().get(0).requestId()).isEqualTo("r1");
  }
}
