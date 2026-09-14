package com.lang.portal.web.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UsageTimeRangeTests {

  private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void defaultsToLast24HoursWhenBothOmitted() {
    UsageTimeRange range = UsageTimeRange.resolve(null, null, FIXED);

    assertThat(range.start()).isEqualTo(Instant.parse("2026-09-09T12:00:00Z"));
    assertThat(range.end()).isEqualTo(Instant.parse("2026-09-10T12:00:00Z"));
  }

  @Test
  void acceptsExplicitSecondPrecisionRange() {
    UsageTimeRange range = UsageTimeRange.resolve(
        "2026-09-10T10:00:00Z", "2026-09-10T11:00:00Z", FIXED);

    assertThat(range.start()).isEqualTo(Instant.parse("2026-09-10T10:00:00Z"));
    assertThat(range.end()).isEqualTo(Instant.parse("2026-09-10T11:00:00Z"));
  }

  @Test
  void convertsEndExclusiveToUpstreamInclusiveSecond() {
    UsageTimeRange range = UsageTimeRange.resolve(
        "2026-09-10T10:00:00Z", "2026-09-10T11:00:00Z", FIXED);

    assertThat(range.upstreamStartTimestamp())
       .isEqualTo(Instant.parse("2026-09-10T10:00:00Z").getEpochSecond());
    assertThat(range.upstreamEndTimestamp())
        .isEqualTo(Instant.parse("2026-09-10T11:00:00Z").getEpochSecond() - 1);
  }

  @Test
  void rejectsSingleSidedRange() {
    assertThatThrownBy(() -> UsageTimeRange.resolve("2026-09-10T10:00:00Z", null, FIXED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingZoneAndSubSecond() {
    assertThatThrownBy(() -> UsageTimeRange.resolve("2026-09-10T10:00:00", "2026-09-10T11:00:00Z", FIXED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UsageTimeRange.resolve(
        "2026-09-10T10:00:00.123Z", "2026-09-10T11:00:00Z", FIXED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsReversedOrOver30Days() {
    assertThatThrownBy(() -> UsageTimeRange.resolve(
        "2026-09-10T11:00:00Z", "2026-09-10T10:00:00Z", FIXED))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UsageTimeRange.resolve(
        "2026-08-01T00:00:00Z", "2026-09-10T12:00:00Z", FIXED))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
