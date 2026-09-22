package com.lang.portal.upstream.newapi.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AggregationBaselineJsonContractTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  Path tempDir;

  @BeforeEach
  void createTempDir() throws Exception {
    Path base = Path.of("target/baseline-json-tests");
    Files.createDirectories(base);
    tempDir = Files.createTempDirectory(base, "case-");
  }

  @Test
  void acceptsWellFormedBaseline() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(p, validBaselineJson());

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ok()).isTrue();
  }

  @Test
  void realRepositoryBaselineIsValidAgainstRealFixtures() {
    Path repo = locateRepoRoot();
    Path baseline = repo.resolve("docs/new-api/aggregation-baseline.json");
    assertThat(baseline).as("baseline json exists at docs/new-api").exists();

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(baseline);

    assertThat(result.errors())
        .as("repository baseline must pass contract: %s", result.errors())
        .isEmpty();
  }

  @Test
  void realRepositoryManifestMatchesRealFixtures() {
    Path repo = locateRepoRoot();
    Path samples = repo.resolve("docs/new-api/samples/aggregation");
    assertThat(samples).as("samples dir exists").isDirectory();

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samples);

    assertThat(result.errors())
        .as("repository manifest must match on-disk fixtures: %s", result.errors())
        .isEmpty();
  }

  @Test
  void realRepositoryFixturesContainNoSensitiveContent() throws Exception {
    Path repo = locateRepoRoot();
    Path samples = repo.resolve("docs/new-api/samples/aggregation");
    AggregationSensitiveScanner scanner = new AggregationSensitiveScanner();
    try (var stream = Files.list(samples)) {
      for (Path p : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
        if ("manifest.json".equals(p.getFileName().toString())) {
          continue;
        }
        JsonNode node = MAPPER.readTree(Files.newInputStream(p));
        var result = scanner.scan(node);
        assertThat(result.clean())
            .as("fixture %s contains sensitive content: %s", p.getFileName(), result.violations())
            .isTrue();
      }
    }
  }

  private static Path locateRepoRoot() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (dir != null && !Files.isDirectory(dir.resolve("docs/new-api"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException("cannot locate repo root from " + System.getProperty("user.dir"));
    }
    return dir;
  }

  @Test
  void rejectsMissingBaselineVersion() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(p, validBaselineJson().replace("\"baselineVersion\": \"p2-2026-09-21-a\",", ""));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors())
        .anyMatch(e -> e.contains("baselineVersion"));
  }

  @Test
  void rejectsMissingFrozenVersion() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(
        p,
        validBaselineJson()
            .replace("\"release\": \"v0.13.2\",", "")
            .replace("\"commit\": \"bee339d279ccecbf8c8a89e14ddbbd902f78bd5d\",", ""));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("release") || e.contains("commit"));
  }

  @Test
  void rejectsMissingMeasuredAt() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(p, validBaselineJson().replace("\"measuredAt\": \"2026-09-21T00:00:00Z\",", ""));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("measuredAt"));
  }

  @Test
  void rejectsUnknownConclusionStatus() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(
        p, validBaselineJson().replace("\"结论\": \"已验证\"", "\"结论\": \"完全没问题\""));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("结论") || e.contains("status"));
  }

  @Test
  void acceptsAllFourStatuses() throws Exception {
    for (String status : new String[] {"已验证", "存在差异", "条件可用", "不可用"}) {
      seedSamples(tempDir);
      Path p = tempDir.resolve("baseline-" + status + ".json");
      Files.writeString(p, validBaselineJson().replace("\"结论\": \"已验证\"", "\"结论\": \"" + status + "\""));
      AggregationBaselineValidator.ValidationResult result = AggregationBaselineValidator.validate(p);
      assertThat(result.errors())
          .as("status=%s", status)
          .noneMatch(e -> e.contains("结论") || e.contains("status"));
    }
  }

  @Test
  void rejectsDuplicateFieldIds() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    JsonNode root = MAPPER.readTree(validBaselineJson());
    com.fasterxml.jackson.databind.node.ArrayNode fields =
        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("fields");
    JsonNode first = fields.get(0);
    fields.insert(1, first.deepCopy());
    Files.writeString(p, MAPPER.writeValueAsString(root));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("duplicate") || e.contains("重复"));
  }

  @Test
  void rejectsDanglingSampleReference() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    Files.writeString(
        p,
        validBaselineJson()
            .replace(
                "\"sampleRef\": \"samples/aggregation/log-self.nonempty.json\"",
                "\"sampleRef\": \"samples/aggregation/log-self.DOES_NOT_EXIST.json\""));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("sample") || e.contains("样例"));
  }

  @Test
  void acceptsExistingSampleReference() throws Exception {
    seedSamples(tempDir);

    Path p = tempDir.resolve("baseline.json");
    Files.writeString(p, validBaselineJson());

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).isEmpty();
  }

  private static void seedSamples(Path tempDir) throws Exception {
    Path samplesDir = tempDir.resolve("samples/aggregation");
    Files.createDirectories(samplesDir);
    Files.writeString(
        samplesDir.resolve("log-self.nonempty.json"), "{\"success\":true,\"data\":{}}");
    Files.writeString(samplesDir.resolve("log-self-stat.nonempty.json"), "{}");
    Files.writeString(samplesDir.resolve("data-self.nonempty.json"), "{}");
    Files.writeString(samplesDir.resolve("user-self.valid.json"), "{}");
    Files.writeString(samplesDir.resolve("topup.nonempty.json"), "{}");
    Files.writeString(samplesDir.resolve("pricing.anon.json"), "{}");
  }

  @Test
  void rejectsDuplicateMetricIds() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    JsonNode root = MAPPER.readTree(validBaselineJson());
    com.fasterxml.jackson.databind.node.ArrayNode metrics =
        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("metrics");
    JsonNode first = metrics.get(0);
    metrics.insert(1, first.deepCopy());
    Files.writeString(p, MAPPER.writeValueAsString(root));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("duplicate") || e.contains("重复"));
  }

  @Test
  void rejectsMetricMissingDenominator() throws Exception {
    seedSamples(tempDir);
    Path p = tempDir.resolve("baseline.json");
    JsonNode root = MAPPER.readTree(validBaselineJson());
    com.fasterxml.jackson.databind.node.ArrayNode metrics =
        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("metrics");
    for (JsonNode metric : metrics) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) metric).remove("分母");
    }
    Files.writeString(p, MAPPER.writeValueAsString(root));

    AggregationBaselineValidator.ValidationResult result =
        AggregationBaselineValidator.validate(p);

    assertThat(result.errors()).anyMatch(e -> e.contains("分母") || e.contains("denominator"));
  }

  private static String validBaselineJson() {
    return """
        {
          "baselineVersion": "p2-2026-09-21-a",
          "frozen": {
            "release": "v0.13.2",
            "commit": "bee339d279ccecbf8c8a89e14ddbbd902f78bd5d",
            "image": "calciumion/new-api:v0.13.2@sha256:0c6aa7afce4747f0fc4fab9c7934d7c2e4b69fda6844065acca6a5e1bf258506"
          },
          "measuredAt": "2026-09-21T00:00:00Z",
          "environment": {
            "description": "本地隔离 Docker Compose",
            "machine": "Darwin 24.6.0 / Apple Silicon",
            "network": "本机回环"
          },
          "interfaces": [
            {
              "id": "log-self",
              "route": "/api/log/self",
              "method": "GET",
              "auth": "session-cookie",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/log-self.nonempty.json"]
            },
            {
              "id": "log-self-stat",
              "route": "/api/log/self/stat",
              "method": "GET",
              "auth": "session-cookie",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/log-self-stat.nonempty.json"]
            },
            {
              "id": "data-self",
              "route": "/api/data/self",
              "method": "GET",
              "auth": "session-cookie",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/data-self.nonempty.json"]
            },
            {
              "id": "user-self",
              "route": "/api/user/self",
              "method": "GET",
              "auth": "session-cookie",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/user-self.valid.json"]
            },
            {
              "id": "topup-self",
              "route": "/api/user/topup/self",
              "method": "GET",
              "auth": "session-cookie",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/topup.nonempty.json"]
            },
            {
              "id": "pricing",
              "route": "/api/pricing",
              "method": "GET",
              "auth": "anonymous-or-session",
              "结论": "已验证",
              "sampleRefs": ["samples/aggregation/pricing.anon.json"]
            }
          ],
          "fields": [
            {
              "id": "log-self.created_time",
              "interface": "log-self",
              "jsonPath": "/data/items/*/created_time",
              "type": "integer",
              "unit": "unix-seconds",
              "nullable": false,
              "适用日志类型": [2, 5],
              "结论": "已验证",
              "sampleRef": "samples/aggregation/log-self.nonempty.json"
            },
            {
              "id": "log-self.quota",
              "interface": "log-self",
              "jsonPath": "/data/items/*/quota",
              "type": "integer",
              "unit": "quota",
              "nullable": true,
              "适用日志类型": [2, 5],
              "结论": "已验证",
              "sampleRef": "samples/aggregation/log-self.nonempty.json"
            }
          ],
          "metrics": [
            {
              "id": "metric.request_count",
              "名称": "请求总数",
              "权威来源": "log-self",
              "分子": "count(items)",
              "分母": "total",
              "结论": "已验证"
            },
            {
              "id": "metric.success_rate",
              "名称": "成功率",
              "权威来源": "log-self",
              "分子": "count(items where type=2)",
              "分母": "total_logs",
              "结论": "已验证"
            },
            {
              "id": "metric.avg_latency",
              "名称": "平均延迟",
              "权威来源": "log-self",
              "分子": "sum(use_time)",
              "分母": "N/A",
              "结论": "已验证"
            }
          ],
          "ledger": {
            "充值": {"来源接口": "topup-self", "方向": "in", "状态字段": "status", "结论": "已验证"},
            "消费": {"来源接口": "log-self", "方向": "out", "状态字段": "type", "结论": "已验证"},
            "退款": {"来源接口": null, "方向": "out", "状态字段": null, "结论": "不可用"}
          },
          "timeRules": {
            "时间单位": "unix-seconds",
            "边界": "[startTime,endTime)",
            "IANA": "服务端 UTC 内部运算，自然日解释按用户时区",
            "结论": "已验证"
          },
          "moneyRules": {
            "quotaPerUsd": "500000",
            "累计阶段": "BigDecimal",
            "舍入": "展示阶段 HALF_UP 保留两位",
            "结论": "已验证"
          },
          "performance": {
            "ranges": ["24h", "7d", "30d"],
            "结论": "条件可用"
          }
        }
        """;
  }
}
