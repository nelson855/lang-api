package com.lang.portal.upstream.newapi.probe;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Explicit opt-in fixture materializer. It never invokes the upstream API. */
class AggregationFixtureMaterializationTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ENABLED = "LANG_PORTAL_AGGREGATION_PROBE_ENABLED";

  @Test
  void materializesSanitizedFixturesFromCapturedRawResponses() throws Exception {
    assumeTrue("true".equals(System.getenv(ENABLED)), "real-probe fixture materialization is opt-in");

    Path repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if (Files.isDirectory(repository.resolve(".." ).resolve("docs"))) {
      repository = repository.resolve("..").normalize();
    }
    Path raw = Path.of(System.getenv().getOrDefault(
        "LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR", repository.resolve("portal-api/target/probe-raw").toString()));
    Path samples = repository.resolve("docs/new-api/samples/aggregation");
    Files.createDirectories(samples);

    AggregationSanitizer sanitizer = new AggregationSanitizer();
    AggregationSensitiveScanner scanner = new AggregationSensitiveScanner();
    for (Fixture fixture : fixtures()) {
      JsonNode input = MAPPER.readTree(Files.readString(raw.resolve("raw").resolve(fixture.source())));
      JsonNode output = switch (fixture.kind()) {
        case LOG -> sanitizer.sanitizeLogList(input);
        case STAT -> sanitizer.sanitizeLogStat(input);
        case HOURLY -> sanitizer.sanitizeHourlyUsage(input);
        case PROFILE -> sanitizer.sanitizeProfile(input);
        case TOPUP -> sanitizer.sanitizeTopupRecords(input);
        case PRICING -> sanitizer.sanitizePricing(input);
      };
      scanner.scanOrThrow(output);
      Files.writeString(samples.resolve(fixture.target()), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n");
    }
    writeManifest(samples);
  }

  private static void writeManifest(Path samples) throws Exception {
    var files = MAPPER.createArrayNode();
    try (var paths = Files.list(samples)) {
      for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
        String file = path.getFileName().toString();
        if ("manifest.json".equals(file)) continue;
        int split = file.indexOf('.');
        String endpoint = file.substring(0, split);
        String scenario = file.substring(split + 1, file.length() - ".json".length());
        files.add(MAPPER.valueToTree(Map.of("file", file, "scenario", endpoint + "." + scenario,
            "interface", endpoint, "sha256", sha256(Files.readAllBytes(path)))));
      }
    }
    var root = MAPPER.createObjectNode();
    root.put("baselineVersion", "p2-2026-09-22-a");
    root.put("measuredAt", java.time.Instant.now().toString());
    root.set("environment", MAPPER.valueToTree(Map.of("description", "本地 Docker Compose 隔离环境", "network", "本机回环 127.0.0.1")));
    root.set("files", files);
    Files.writeString(samples.resolve("manifest.json"), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
  }

  private static String sha256(byte[] bytes) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder out = new StringBuilder();
    for (byte value : hash) out.append(String.format("%02x", value));
    return out.toString();
  }

  private static List<Fixture> fixtures() {
    return List.of(
        log("nonempty"), log("empty"), log("unauth"), log("bad-page"), log("time-edge"), log("type-1"), log("type-2"), log("type-5"),
        fixture("log-self-stat", "nonempty", Kind.STAT), fixture("log-self-stat", "empty", Kind.STAT), fixture("log-self-stat", "unauth", Kind.STAT), fixture("log-self-stat", "bad-time", Kind.STAT), fixture("log-self-stat", "adjacent", Kind.STAT),
        fixture("data-self", "nonempty", Kind.HOURLY), fixture("data-self", "empty", Kind.HOURLY), fixture("data-self", "unauth", Kind.HOURLY), fixture("data-self", "bad-time", Kind.HOURLY), fixture("data-self", "hourly-edge", Kind.HOURLY), fixture("data-self", "latency", Kind.HOURLY),
        fixture("user-self", "valid", Kind.PROFILE), fixture("user-self", "unauth", Kind.PROFILE),
        fixture("topup", "empty", Kind.TOPUP), fixture("topup", "unauth", Kind.TOPUP), fixture("topup", "bad-page", Kind.TOPUP),
        fixture("pricing", "anon", Kind.PRICING), fixture("pricing", "auth", Kind.PRICING), fixture("pricing", "nonempty", Kind.PRICING));
  }

  private static Fixture log(String scenario) { return fixture("log-self", scenario, Kind.LOG); }

  private static Fixture fixture(String endpoint, String scenario, Kind kind) {
    return new Fixture(endpoint + "/" + scenario + ".json", endpoint + "." + scenario + ".json", kind);
  }

  private record Fixture(String source, String target, Kind kind) {}
  private enum Kind { LOG, STAT, HOURLY, PROFILE, TOPUP, PRICING }
}
