package com.lang.portal.upstream.newapi.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AggregationManifestContractTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  Path samplesDir;

  @BeforeEach
  void createSamplesDir() throws Exception {
    Path base = Path.of("target/manifest-tests");
    Files.createDirectories(base);
    samplesDir = Files.createTempDirectory(base, "case-").resolve("samples/aggregation");
    Files.createDirectories(samplesDir);
  }

  @Test
  void acceptsValidManifest() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(manifest, manifestWithRealHashes());

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).isEmpty();
    assertThat(result.ok()).isTrue();
  }

  @Test
  void rejectsMissingBaselineVersion() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(
        manifest, validManifestJson().replace("\"baselineVersion\": \"p2-2026-09-21-a\",", ""));

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("baselineVersion"));
  }

  @Test
  void rejectsMissingScenario() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    JsonNode root = MAPPER.readTree(validManifestJson());
    com.fasterxml.jackson.databind.node.ArrayNode files =
        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("files");
    ((com.fasterxml.jackson.databind.node.ObjectNode) files.get(0)).remove("scenario");
    Files.writeString(manifest, MAPPER.writeValueAsString(root));

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("scenario"));
  }

  @Test
  void rejectsFrozenVersionMismatch() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    JsonNode root = MAPPER.readTree(manifestWithRealHashes());
    ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("baselineVersion", "DIFFERENT");
    Files.writeString(manifest, MAPPER.writeValueAsString(root));

    Path baseline = samplesDir.resolve("../aggregation-baseline.json").normalize();
    Files.writeString(baseline, """
        {
          "baselineVersion": "p2-2026-09-21-a",
          "frozen": {"release": "v0.13.2", "commit": "c", "image": "i"},
          "measuredAt": "2026-09-21T00:00:00Z",
          "environment": {"description": "x", "machine": "y", "network": "z"},
          "interfaces": [], "fields": [], "metrics": [],
          "ledger": {
            "充值": {"方向": "in", "结论": "已验证"},
            "消费": {"方向": "out", "结论": "已验证"},
            "退款": {"方向": "out", "结论": "不可用"}
          },
          "timeRules": {"时间单位": "s", "边界": "[a,b)", "结论": "已验证"},
          "moneyRules": {"quotaPerUsd": "1", "累计阶段": "BigDecimal", "舍入": "r", "结论": "已验证"}
        }
        """);

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("baselineVersion"));
  }

  @Test
  void rejectsMissingMeasuredAt() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    JsonNode root = MAPPER.readTree(validManifestJson());
    ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("measuredAt");
    Files.writeString(manifest, MAPPER.writeValueAsString(root));

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("measuredAt"));
  }

  @Test
  void rejectsMissingEnvironmentSummary() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    JsonNode root = MAPPER.readTree(validManifestJson());
    ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("environment");
    Files.writeString(manifest, MAPPER.writeValueAsString(root));

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("environment"));
  }

  @Test
  void rejectsDanglingFileReference() throws Exception {
    seedSixSamples();
    Files.delete(samplesDir.resolve("pricing.anon.json"));
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(manifest, validManifestJson());

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("not found") || e.contains("file"));
  }

  @Test
  void rejectsFileNotListedInManifest() throws Exception {
    seedSixSamples();
    Files.writeString(samplesDir.resolve("orphan.json"), "{}");
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(manifest, validManifestJson());

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("orphan") || e.contains("not in manifest"));
  }

  @Test
  void rejectsTamperedFileHash() throws Exception {
    seedSixSamples();
    Path target = samplesDir.resolve("log-self.nonempty.json");
    Files.writeString(target, "{\"tampered\":true}");
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(manifest, validManifestJson());

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).anyMatch(e -> e.contains("sha256") || e.contains("digest") || e.contains("hash"));
  }

  @Test
  void acceptsCorrectFileHashes() throws Exception {
    seedSixSamples();
    Path manifest = samplesDir.resolve("manifest.json");
    Files.writeString(manifest, manifestWithRealHashes());

    AggregationManifestValidator.ValidationResult result =
        AggregationManifestValidator.validate(samplesDir);

    assertThat(result.errors()).isEmpty();
  }

  private void seedSixSamples() throws Exception {
    Files.writeString(samplesDir.resolve("log-self.nonempty.json"), "{\"a\":1}");
    Files.writeString(samplesDir.resolve("log-self-stat.nonempty.json"), "{\"b\":2}");
    Files.writeString(samplesDir.resolve("data-self.nonempty.json"), "{\"c\":3}");
    Files.writeString(samplesDir.resolve("user-self.valid.json"), "{\"d\":4}");
    Files.writeString(samplesDir.resolve("topup.nonempty.json"), "{\"e\":5}");
    Files.writeString(samplesDir.resolve("pricing.anon.json"), "{\"f\":6}");
  }

  private String manifestWithRealHashes() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    com.fasterxml.jackson.databind.node.ObjectNode root =
        (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(validManifestJson());
    com.fasterxml.jackson.databind.node.ArrayNode files =
        (com.fasterxml.jackson.databind.node.ArrayNode) root.get("files");
    for (JsonNode f : files) {
      String name = f.get("file").asText();
      byte[] content = Files.readAllBytes(samplesDir.resolve(name));
      byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      ((com.fasterxml.jackson.databind.node.ObjectNode) f).put("sha256", sb.toString());
    }
    return mapper.writeValueAsString(root);
  }

  private static String validManifestJson() {
    return """
        {
          "baselineVersion": "p2-2026-09-21-a",
          "measuredAt": "2026-09-21T00:00:00Z",
          "environment": {
            "description": "本地隔离 Docker Compose",
            "machine": "Darwin 24.6.0",
            "network": "本机回环"
          },
          "files": [
            {
              "file": "log-self.nonempty.json",
              "scenario": "log-self.nonempty",
              "interface": "log-self",
              "sha256": "PLACEHOLDER"
            },
            {
              "file": "log-self-stat.nonempty.json",
              "scenario": "log-self-stat.nonempty",
              "interface": "log-self-stat",
              "sha256": "PLACEHOLDER"
            },
            {
              "file": "data-self.nonempty.json",
              "scenario": "data-self.nonempty",
              "interface": "data-self",
              "sha256": "PLACEHOLDER"
            },
            {
              "file": "user-self.valid.json",
              "scenario": "user-self.valid",
              "interface": "user-self",
              "sha256": "PLACEHOLDER"
            },
            {
              "file": "topup.nonempty.json",
              "scenario": "topup.nonempty",
              "interface": "topup-self",
              "sha256": "PLACEHOLDER"
            },
            {
              "file": "pricing.anon.json",
              "scenario": "pricing.anon",
              "interface": "pricing",
              "sha256": "PLACEHOLDER"
            }
          ]
        }
        """;
  }
}
