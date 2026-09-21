package com.lang.portal.upstream.newapi.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AggregationBaselineValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Set<String> ALLOWED_STATUSES =
      Set.of("已验证", "存在差异", "条件可用", "不可用");

  private AggregationBaselineValidator() {}

  public record ValidationResult(List<String> errors) {
    public boolean ok() {
      return errors.isEmpty();
    }
  }

  public static ValidationResult validate(Path baselinePath) {
    List<String> errors = new ArrayList<>();
    if (baselinePath == null || !Files.exists(baselinePath)) {
      errors.add("baseline file not found: " + baselinePath);
      return new ValidationResult(errors);
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(Files.newInputStream(baselinePath));
    } catch (IOException e) {
      errors.add("baseline file not parseable: " + e.getMessage());
      return new ValidationResult(errors);
    }
    validateBasics(root, errors);
    validateInterfaces(root, baselinePath, errors);
    validateFields(root, baselinePath, errors);
    validateMetrics(root, errors);
    validateLedger(root, errors);
    validateTimeRules(root, errors);
    validateMoneyRules(root, errors);
    return new ValidationResult(errors);
  }

  private static void validateBasics(JsonNode root, List<String> errors) {
    require(root, "baselineVersion", errors);
    require(root, "frozen", errors);
    require(root, "measuredAt", errors);
    require(root, "environment", errors);
    if (!root.has("frozen")) {
      return;
    }
    JsonNode frozen = root.get("frozen");
    require(frozen, "release", errors);
    require(frozen, "commit", errors);
    require(frozen, "image", errors);
  }

  private static void validateInterfaces(JsonNode root, Path basePath, List<String> errors) {
    JsonNode interfaces = root.get("interfaces");
    if (interfaces == null || !interfaces.isArray() || interfaces.isEmpty()) {
      errors.add("interfaces must be a non-empty array");
      return;
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode iface : interfaces) {
      require(iface, "id", errors);
      require(iface, "route", errors);
      require(iface, "method", errors);
      require(iface, "auth", errors);
      checkStatus(iface, errors);
      String id = iface.has("id") ? iface.get("id").asText() : null;
      if (id != null && !ids.add(id)) {
        errors.add("duplicate interface id: " + id);
      }
      JsonNode sampleRefs = iface.get("sampleRefs");
      if (sampleRefs != null && sampleRefs.isArray()) {
        for (JsonNode ref : sampleRefs) {
          checkSampleRefExists(basePath, ref.asText(), errors);
        }
      }
    }
  }

  private static void validateFields(JsonNode root, Path basePath, List<String> errors) {
    JsonNode fields = root.get("fields");
    if (fields == null || !fields.isArray()) {
      return;
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode field : fields) {
      require(field, "id", errors);
      require(field, "interface", errors);
      require(field, "jsonPath", errors);
      require(field, "type", errors);
      require(field, "unit", errors);
      require(field, "sampleRef", errors);
      checkStatus(field, errors);
      String id = field.has("id") ? field.get("id").asText() : null;
      if (id != null && !ids.add(id)) {
        errors.add("duplicate field id: " + id);
      }
      if (field.has("sampleRef")) {
        checkSampleRefExists(basePath, field.get("sampleRef").asText(), errors);
      }
    }
  }

  private static void validateMetrics(JsonNode root, List<String> errors) {
    JsonNode metrics = root.get("metrics");
    if (metrics == null || !metrics.isArray()) {
      errors.add("metrics must be an array");
      return;
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode metric : metrics) {
      require(metric, "id", errors);
      require(metric, "名称", errors);
      require(metric, "权威来源", errors);
      require(metric, "分子", errors);
      require(metric, "分母", errors);
      checkStatus(metric, errors);
      String id = metric.has("id") ? metric.get("id").asText() : null;
      if (id != null && !ids.add(id)) {
        errors.add("duplicate metric id: " + id);
      }
    }
  }

  private static void validateLedger(JsonNode root, List<String> errors) {
    JsonNode ledger = root.get("ledger");
    if (ledger == null || !ledger.isObject()) {
      errors.add("ledger must be an object");
      return;
    }
    for (String kind : new String[] {"充值", "消费", "退款"}) {
      JsonNode entry = ledger.get(kind);
      if (entry == null) {
        errors.add("ledger missing: " + kind);
        continue;
      }
      require(entry, "方向", errors);
      require(entry, "结论", errors);
      checkStatus(entry, errors);
    }
  }

  private static void validateTimeRules(JsonNode root, List<String> errors) {
    JsonNode time = root.get("timeRules");
    if (time == null) {
      errors.add("timeRules must be present");
      return;
    }
    require(time, "时间单位", errors);
    require(time, "边界", errors);
    require(time, "结论", errors);
    checkStatus(time, errors);
  }

  private static void validateMoneyRules(JsonNode root, List<String> errors) {
    JsonNode money = root.get("moneyRules");
    if (money == null) {
      errors.add("moneyRules must be present");
      return;
    }
    require(money, "quotaPerUsd", errors);
    require(money, "累计阶段", errors);
    require(money, "舍入", errors);
    require(money, "结论", errors);
    checkStatus(money, errors);
  }

  private static void checkStatus(JsonNode node, List<String> errors) {
    JsonNode status = node.get("结论");
    if (status == null || status.isNull()) {
      errors.add("missing 结论 status");
      return;
    }
    String s = status.asText();
    if (!ALLOWED_STATUSES.contains(s)) {
      errors.add("illegal 结论 status: " + s + " (allowed: " + ALLOWED_STATUSES + ")");
    }
  }

  private static void checkSampleRefExists(Path baselinePath, String ref, List<String> errors) {
    if (ref == null || ref.isBlank()) {
      errors.add("empty sample ref");
      return;
    }
    Path parent = baselinePath.getParent();
    if (parent == null) {
      parent = Path.of(".");
    }
    Path samplePath = parent.resolve(ref).normalize();
    if (!Files.exists(samplePath)) {
      errors.add("sample ref not found: " + ref);
    }
  }

  private static void require(JsonNode node, String field, List<String> errors) {
    if (node == null || !node.has(field) || node.get(field).isNull()
        || (node.get(field).isTextual() && node.get(field).asText().isBlank())) {
      errors.add("missing required field: " + field);
    }
  }
}
