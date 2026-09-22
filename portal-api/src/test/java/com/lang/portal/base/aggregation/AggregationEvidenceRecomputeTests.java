package com.lang.portal.base.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggregationEvidenceRecomputeTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonNode fixtureItems() throws Exception {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (dir != null && !Files.isDirectory(dir.resolve("docs/new-api"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException("cannot locate repo root");
    }
    JsonNode root =
        MAPPER.readTree(
            Files.newInputStream(dir.resolve("docs/new-api/samples/aggregation/log-self.nonempty.json")));
    return root.path("data").path("items");
  }

  @Test
  void frozenFixtureReproducesVerifiedTokenLatencyAndKeyCounts() throws Exception {
    JsonNode items = fixtureItems();
    List<JsonNode> successRows = new ArrayList<>();
    for (JsonNode item : items) {
      if (item.path("type").asInt(-1) == 2) {
        successRows.add(item);
      }
    }
    assertThat(successRows).hasSize(8);

    long promptSum = 0;
    long completionSum = 0;
    long useTimeSum = 0;
    for (JsonNode row : successRows) {
      promptSum += row.path("prompt_tokens").asLong();
      completionSum += row.path("completion_tokens").asLong();
      useTimeSum += row.path("use_time").asLong();
    }
    assertThat(promptSum).isEqualTo(248L);
    assertThat(completionSum).isEqualTo(160L);
    assertThat(promptSum + completionSum).isEqualTo(408L);
    assertThat(useTimeSum).isEqualTo(20L);

    Set<String> tokenIds = new HashSet<>();
    for (JsonNode item : items) {
      tokenIds.add(item.path("token_id").asText());
    }
    assertThat(tokenIds).hasSize(3);

    AggregationAccumulator accumulator = new AggregationAccumulator();
    for (JsonNode row : successRows) {
      accumulator.add(
          new AggregationLogRecord(
              Instant.EPOCH,
              AggregationLogResult.SUCCESS,
              null,
              row.path("model_name").asText(null),
              row.path("request_id").asText(null),
              row.path("token_name").asText(null),
              row.path("prompt_tokens").asLong(),
              row.path("completion_tokens").asLong(),
              row.path("use_time").asLong() * 1000L,
              row.path("is_stream").asBoolean(false),
              row.path("quota").asLong()));
    }
    AggregationTotals totals = accumulator.totals();
    assertThat(totals.inputTokens()).isEqualTo(248L);
    assertThat(totals.outputTokens()).isEqualTo(160L);
    assertThat(totals.durationMs()).isEqualTo(20000L);
    assertThat(totals.durationMs() / totals.recordCount()).isEqualTo(2500L);
    assertThat(totals.rawQuota()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void requestTotalSuccessRateAndUsdStayUnavailableOnFrozenBaseline() {
    assertThat(
            AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.REQUEST_TOTAL))
        .isNotEqualTo(FieldSupport.VERIFIED);
    assertThat(
            AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.SUCCESS_RATE))
        .isNotEqualTo(FieldSupport.VERIFIED);
    assertThat(
            AggregationBaselinePolicy.metricStatus("p2-2026-09-22-a", AggregationMetric.USD_AMOUNT))
        .isNotEqualTo(FieldSupport.VERIFIED);
    assertThat(AggregationFormalValues.formalUsdAmount(BigDecimal.ZERO, "p2-2026-09-22-a", 500000L))
        .isEmpty();
    assertThat(AggregationFormalValues.formalSuccessRate(8L, 9L, "p2-2026-09-22-a")).isEmpty();
  }
}
