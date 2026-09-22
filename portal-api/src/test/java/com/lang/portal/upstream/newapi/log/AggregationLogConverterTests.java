package com.lang.portal.upstream.newapi.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregationLogConverterTests {

  @Test
  void verifiedFieldsEnterPortalRecordWithoutUpstreamDto() {
    NewApiLogRecord upstream =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:00:00Z"),
            "probe-key-01",
            "gpt-test",
            NewApiLogResult.SUCCESS,
            10L,
            20L,
            3000L,
            true,
            500L,
            "req-1",
            9L);
    AggregationLogRecord record = AggregationLogConverter.fromUpstream(upstream, "p2-2026-09-22-a");
    assertThat(record.occurredAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(record.inputTokens()).isEqualTo(10L);
    assertThat(record.outputTokens()).isEqualTo(20L);
    assertThat(record.durationMs()).isEqualTo(3000L);
    assertThat(record.tokenId()).isEqualTo(9L);
    assertThat(record.model()).isEqualTo("gpt-test");
    assertThat(record.requestId()).isEqualTo("req-1");
    assertThat(record.rawQuota()).isEqualTo(500L);
  }

  @Test
  void missingOptionalFieldsKeepMissingSemantics() {
    NewApiLogRecord upstream =
        new NewApiLogRecord(
            Instant.parse("2026-09-01T00:00:00Z"),
            null,
            null,
            NewApiLogResult.ERROR,
            0L,
            0L,
            0L,
            false,
            0L,
            null,
            null);
    AggregationLogRecord record = AggregationLogConverter.fromUpstream(upstream, "p2-2026-09-22-a");
    assertThat(record.model()).isNull();
    assertThat(record.requestId()).isNull();
    assertThat(record.tokenId()).isNull();
  }
}
