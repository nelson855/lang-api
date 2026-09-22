package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AggregationPagedReaderTests {

  private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

  private static AggregationReadBudget budget(int maxPages, int maxRecords) {
    return new AggregationReadBudget(maxPages, maxRecords, FIXED.instant().plusSeconds(30), FIXED);
  }

  @Test
  void completeSinglePageIsReturned() {
    AggregationReadBudget readBudget = budget(10, 200);
    List<String> result =
        AggregationPagedReader.readAll(readBudget, 20, page -> new AggregationPagedReader.Page<>(2, List.of("a", "b")));
    assertThat(result).containsExactly("a", "b");
  }

  @Test
  void completeMultiPagesAreReturned() {
    AggregationReadBudget readBudget = budget(10, 200);
    List<String> result =
        AggregationPagedReader.readAll(
            readBudget,
            2,
            page ->
                page == 1
                    ? new AggregationPagedReader.Page<>(3, List.of("a", "b"))
                    : new AggregationPagedReader.Page<>(3, List.of("c")));
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void firstPageTotalBeyondLimitRejectsWithoutFurtherFetch() {
    AggregationReadBudget readBudget = budget(10, 2);
    AtomicInteger fetches = new AtomicInteger();
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    20,
                    page -> {
                      fetches.incrementAndGet();
                      return new AggregationPagedReader.Page<>(5, List.of("a", "b"));
                    }))
        .isInstanceOf(PortalException.class);
    assertThat(fetches.get()).isEqualTo(1);
  }

  @Test
  void pageCountBeyondLimitRejects() {
    AggregationReadBudget readBudget = budget(1, 200);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page ->
                        page == 1
                            ? new AggregationPagedReader.Page<>(3, List.of("a", "b"))
                            : new AggregationPagedReader.Page<>(3, List.of("c"))))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void recordCountBeyondLimitRejects() {
    AggregationReadBudget readBudget = budget(10, 2);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page ->
                        page == 1
                            ? new AggregationPagedReader.Page<>(3, List.of("a", "b"))
                            : new AggregationPagedReader.Page<>(3, List.of("c"))))
        .isInstanceOf(PortalException.class);
  }

  @Test
  void changingTotalFailsAsIncomplete() {
    AggregationReadBudget readBudget = budget(10, 200);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page ->
                        page == 1
                            ? new AggregationPagedReader.Page<>(3, List.of("a", "b"))
                            : new AggregationPagedReader.Page<>(4, List.of("c"))))
        .isInstanceOf(UpstreamException.class);
  }

  @Test
  void duplicatePageFailsAsIncomplete() {
    AggregationReadBudget readBudget = budget(10, 200);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page -> new AggregationPagedReader.Page<>(4, List.of("a", "b"))))
        .isInstanceOf(UpstreamException.class);
  }

  @Test
  void earlyEmptyPageFailsAsIncomplete() {
    AggregationReadBudget readBudget = budget(10, 200);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page ->
                        page == 1
                            ? new AggregationPagedReader.Page<>(3, List.of("a", "b"))
                            : new AggregationPagedReader.Page<>(3, List.of())))
        .isInstanceOf(UpstreamException.class);
  }

  @Test
  void abnormalShortPageFailsAsIncomplete() {
    AggregationReadBudget readBudget = budget(10, 200);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    readBudget,
                    2,
                    page ->
                        page == 1
                            ? new AggregationPagedReader.Page<>(3, List.of("a"))
                            : new AggregationPagedReader.Page<>(3, List.of("b", "c"))))
        .isInstanceOf(UpstreamException.class);
  }
}
