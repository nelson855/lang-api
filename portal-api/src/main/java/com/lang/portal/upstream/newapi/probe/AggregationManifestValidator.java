package com.lang.portal.upstream.newapi.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class AggregationManifestValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String MANIFEST_FILE = "manifest.json";

  private AggregationManifestValidator() {}

  public record ValidationResult(List<String> errors) {
    public boolean ok() {
      return errors.isEmpty();
    }
  }

  public static ValidationResult validate(Path samplesDir) {
    List<String> errors = new ArrayList<>();
    Path manifestPath = samplesDir.resolve(MANIFEST_FILE);
    if (!Files.exists(manifestPath)) {
      errors.add("manifest.json not found in " + samplesDir);
      return new ValidationResult(errors);
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(Files.newInputStream(manifestPath));
    } catch (IOException e) {
      errors.add("manifest.json not parseable: " + e.getMessage());
      return new ValidationResult(errors);
    }
    require(root, "baselineVersion", errors);
    require(root, "measuredAt", errors);
    require(root, "environment", errors);
    require(root, "files", errors);

    Path baselinePath = samplesDir.resolve("../aggregation-baseline.json").normalize();
    if (Files.exists(baselinePath) && root.has("baselineVersion")) {
      try {
        JsonNode baseline = MAPPER.readTree(Files.newInputStream(baselinePath));
        if (baseline.has("baselineVersion")) {
          String manifestV = root.get("baselineVersion").asText();
          String baselineV = baseline.get("baselineVersion").asText();
          if (!manifestV.equals(baselineV)) {
            errors.add(
                "baselineVersion mismatch: manifest=" + manifestV + " baseline=" + baselineV);
          }
        }
      } catch (IOException ignored) {
      }
    }

    Set<String> declaredFiles = new HashSet<>();
    JsonNode files = root.get("files");
    if (files != null && files.isArray()) {
      for (JsonNode f : files) {
        require(f, "file", errors);
        require(f, "scenario", errors);
        require(f, "interface", errors);
        require(f, "sha256", errors);
        if (!f.has("file")) {
          continue;
        }
        String name = f.get("file").asText();
        declaredFiles.add(name);
        Path samplePath = samplesDir.resolve(name).normalize();
        if (!Files.exists(samplePath)) {
          errors.add("file not found: " + name);
          continue;
        }
        if (!f.has("sha256")) {
          continue;
        }
        String expectedHash = f.get("sha256").asText();
        if ("PLACEHOLDER".equals(expectedHash)) {
          errors.add("sha256 is PLACEHOLDER for " + name);
          continue;
        }
        String actualHash;
        try {
          actualHash = sha256Hex(Files.readAllBytes(samplePath));
        } catch (IOException e) {
          errors.add("cannot read " + name + ": " + e.getMessage());
          continue;
        }
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
          errors.add("sha256 mismatch for " + name);
        }
      }
    }

    try (Stream<Path> stream = Files.list(samplesDir)) {
      stream
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .filter(p -> !MANIFEST_FILE.equals(p.getFileName().toString()))
          .forEach(
              p -> {
                String name = p.getFileName().toString();
                if (!declaredFiles.contains(name)) {
                  errors.add("file not in manifest: " + name);
                }
              });
    } catch (IOException e) {
      errors.add("cannot list samples dir: " + e.getMessage());
    }
    return new ValidationResult(errors);
  }

  private static void require(JsonNode node, String field, List<String> errors) {
    if (node == null || !node.has(field) || node.get(field).isNull()
        || (node.get(field).isTextual() && node.get(field).asText().isBlank())) {
      errors.add("missing required field: " + field);
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(data);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
