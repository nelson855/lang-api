package com.lang.portal.infrastructure.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AggregationReadBudgetTimeoutTests {

  static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone = ZoneId.of("UTC");

    MutableClock(Instant initial) {
      this.now = new AtomicReference<>(initial);
    }

    void advance(Duration duration) {
      now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  @Test
  void callAfterDeadlineMapsToUpstreamTimeout() {
    Instant start = Instant.parse("2026-09-01T00:00:00Z");
    MutableClock clock = new MutableClock(start.plusSeconds(31));
    AggregationReadBudget budget = new AggregationReadBudget(10, 200, start.plusSeconds(30), clock);
    assertThatThrownBy(budget::checkBeforeCall)
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT);
  }

  @Test
  void deadlineDuringMultiPageReadFailsWholeAggregation() {
    Instant start = Instant.parse("2026-09-01T00:00:00Z");
    MutableClock clock = new MutableClock(start);
    AggregationReadBudget budget = new AggregationReadBudget(10, 200, start.plusSeconds(30), clock);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    budget,
                    2,
                    page -> {
                      if (page == 1) {
                        clock.advance(Duration.ofSeconds(31));
                        return new AggregationPagedReader.Page<>(3, List.of("a", "b"));
                      }
                      return new AggregationPagedReader.Page<>(3, List.of("c"));
                    }))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_TIMEOUT);
    assertThat(budget.usedPages()).isEqualTo(1);
  }

  @Test
  void protectionLimitMapsToInvalidArgumentWithSafeMessage() {
    Instant start = Instant.parse("2026-09-01T00:00:00Z");
    MutableClock clock = new MutableClock(start);
    AggregationReadBudget budget = new AggregationReadBudget(10, 2, start.plusSeconds(30), clock);
    assertThatThrownBy(
            () ->
                AggregationPagedReader.readAll(
                    budget, 20, page -> new AggregationPagedReader.Page<>(5, List.of("a", "b"))))
        .isInstanceOf(PortalException.class)
        .matches(
            e ->
                ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT
                    && e.getMessage() != null
                    && e.getMessage().contains("缩小"));
  }
}
