package com.lang.portal.upstream.newapi.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NewApiLogQueryTests {

  @Test
  void encodesKeyExactAndModelLikeWithEscaping() {
    NewApiLogQuery query =
        NewApiLogQuery.of(1, 20, "probe-key-01", "a%b_c!d", 1789180800L, 1789184399L);

    String path = query.toPath(NewApiLogResult.SUCCESS);

    assertThat(path).startsWith("/api/log/self?");
    assertThat(path).contains("token_name=probe-key-01");
    assertThat(path).contains("model_name=%25a%21%25b%21_c%21%21d%25");
    assertThat(path).contains("start_timestamp=1789180800");
    assertThat(path).contains("end_timestamp=1789184399");
    assertThat(path).doesNotContain("user_id");
    assertThat(path).doesNotContain("channel");
  }

  @Test
  void convertsExclusiveEndToInclusiveSecond() {
    com.lang.portal.web.usage.UsageTimeRange range =
        com.lang.portal.web.usage.UsageTimeRange.resolve(
            "2026-09-10T10:00:00Z",
            "2026-09-10T11:00:00Z",
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T12:00:00Z"), java.time.ZoneOffset.UTC));
    NewApiLogQuery query = NewApiLogQuery.fromRange(1, 20, null, null, range);

    String path = query.toPath(NewApiLogResult.SUCCESS);

    assertThat(path).contains("start_timestamp=1789034400");
    assertThat(path).contains("end_timestamp=1789037999");
  }

  @Test
  void rejectsOverlongFilters() {
    String longKey = "k".repeat(65);
    assertThatThrownBy(() -> NewApiLogQuery.of(1, 20, longKey, null, 1000L, 2000L))
        .isInstanceOf(IllegalArgumentException.class);
    String longModel = "m".repeat(129);
    assertThatThrownBy(() -> NewApiLogQuery.of(1, 20, null, longModel, 1000L, 2000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownResultType() {
    assertThatThrownBy(() -> NewApiLogQuery.of(0, 20, null, null, 1000L, 2000L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
