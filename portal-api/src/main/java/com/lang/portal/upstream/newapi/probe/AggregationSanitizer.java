package com.lang.portal.upstream.newapi.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AggregationSanitizer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<String> LOG_LIST_ITEM_WHITELIST =
      List.of(
          "created_time",
          "type",
          "token_name",
          "model_name",
          "quota",
          "prompt_tokens",
          "completion_tokens",
          "use_time",
          "is_stream",
          "request_id",
          "channel",
          "group",
          "user_id",
          "token_id",
          "first_token_time",
          "pre_consumed_quota",
          "ratio");

  private static final List<String> TOPUP_ITEM_WHITELIST =
      List.of(
          "id", "user_id", "amount", "currency", "status", "trade_no", "created_time", "paid_time");

  private static final List<String> PROFILE_WHITELIST =
      List.of(
          "id", "username", "email", "display_name", "role", "status", "quota", "used_quota",
          "group", "aff_count", "aff_quota", "aff_history_quota", "invter_user_id");

  private final Map<String, String> placeholderByOriginal = new HashMap<>();
  private final Map<String, AtomicInteger> counters = new HashMap<>();

  public JsonNode sanitizeLogList(JsonNode input) {
    ObjectNode root = input.deepCopy();
    JsonNode data = root.get("data");
    if (data != null && data.isObject()) {
      JsonNode items = data.get("items");
      if (items != null && items.isArray()) {
        ArrayNode newItems = MAPPER.createArrayNode();
        for (JsonNode item : items) {
          newItems.add(sanitizeLogItem(item));
        }
        ((ObjectNode) data).set("items", newItems);
      }
    }
    return root;
  }

  public JsonNode sanitizeLogStat(JsonNode input) {
    ObjectNode root = input.deepCopy();
    return root;
  }

  public JsonNode sanitizeHourlyUsage(JsonNode input) {
    ObjectNode root = input.deepCopy();
    return root;
  }

  public JsonNode sanitizeTopupRecords(JsonNode input) {
    ObjectNode root = input.deepCopy();
    JsonNode data = root.get("data");
    if (data != null && data.isObject()) {
      JsonNode items = data.get("items");
      if (items != null && items.isArray()) {
        ArrayNode newItems = MAPPER.createArrayNode();
        for (JsonNode item : items) {
          newItems.add(sanitizeTopupItem(item));
        }
        ((ObjectNode) data).set("items", newItems);
      }
    }
    return root;
  }

  public JsonNode sanitizeTopupInfo(JsonNode input) {
    return input.deepCopy();
  }

  public JsonNode sanitizeProfile(JsonNode input) {
    ObjectNode root = input.deepCopy();
    JsonNode data = root.get("data");
    if (data != null && data.isObject()) {
      ObjectNode newData = MAPPER.createObjectNode();
      Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (!PROFILE_WHITELIST.contains(field.getKey())) {
          continue;
        }
        newData.set(field.getKey(), sanitizeProfileValue(field.getKey(), field.getValue()));
      }
      root.set("data", newData);
    }
    return root;
  }

  public JsonNode sanitizePricing(JsonNode input) {
    ObjectNode root = input.deepCopy();
    JsonNode data = root.get("data");
    if (data != null && data.isObject()) {
      JsonNode items = data.get("data");
      if (items != null && items.isArray()) {
        ArrayNode newItems = MAPPER.createArrayNode();
        for (JsonNode item : items) {
          newItems.add(sanitizePricingItem(item));
        }
        ((ObjectNode) data).set("data", newItems);
      }
    }
    return root;
  }

  public Map<String, String> getMappingSnapshot() {
    throw new UnsupportedOperationException(
        "placeholder mapping is in-memory only and must not be exposed");
  }

  private ObjectNode sanitizeLogItem(JsonNode item) {
    ObjectNode result = MAPPER.createObjectNode();
    for (String key : LOG_LIST_ITEM_WHITELIST) {
      JsonNode value = item.get(key);
      if (value == null) {
        continue;
      }
      switch (key) {
        case "token_name" -> result.set(key, textOrNull(placeholderFor("TOKEN", value.asText())));
        case "user_id" -> {
          if (value.isNumber()) {
            result.set(
                key,
                textOrNull(placeholderFor("USER", "uid-" + value.asLong())));
          } else {
            result.set(key, value);
          }
        }
        case "token_id" -> {
          if (value.isNumber()) {
            result.set(
                key,
                textOrNull(placeholderFor("TOKENID", "tid-" + value.asLong())));
          } else {
            result.set(key, value);
          }
        }
        case "channel" -> {
          if (value.isNumber()) {
            result.set(
                key,
                textOrNull(placeholderFor("CHANNEL", "ch-" + value.asLong())));
          } else {
            result.set(key, value);
          }
        }
        default -> result.set(key, value);
      }
    }
    return result;
  }

  private ObjectNode sanitizeTopupItem(JsonNode item) {
    ObjectNode result = MAPPER.createObjectNode();
    for (String key : TOPUP_ITEM_WHITELIST) {
      JsonNode value = item.get(key);
      if (value == null) {
        continue;
      }
      switch (key) {
        case "trade_no" -> result.set(
            key, textOrNull(placeholderFor("TRADE", value.asText())));
        case "user_id" -> {
          if (value.isNumber()) {
            result.set(
                key,
                textOrNull(placeholderFor("USER", "uid-" + value.asLong())));
          } else {
            result.set(key, value);
          }
        }
        default -> result.set(key, value);
      }
    }
    return result;
  }

  private ObjectNode sanitizePricingItem(JsonNode item) {
    ObjectNode result = item.deepCopy();
    JsonNode owner = result.get("owner");
    if (owner != null && owner.isNumber()) {
      result.set("owner", textOrNull(placeholderFor("USER", "uid-" + owner.asLong())));
    }
    JsonNode createdBy = result.get("created_by");
    if (createdBy != null && createdBy.isNumber()) {
      result.set(
          "created_by", textOrNull(placeholderFor("USER", "uid-" + createdBy.asLong())));
    }
    return result;
  }

  private JsonNode sanitizeProfileValue(String key, JsonNode value) {
    if ("id".equals(key)) {
      if (value.isNumber()) {
        return textOrNull(placeholderFor("USER", "uid-" + value.asLong()));
      }
      return value;
    }
    if (!value.isTextual()) {
      return value;
    }
    String text = value.asText();
    if (text == null || text.isEmpty()) {
      return value;
    }
    return switch (key) {
      case "username" -> textOrNull(placeholderFor("USERNAME", "u:" + text));
      case "email" -> textOrNull(placeholderFor("EMAIL", "e:" + text));
      case "display_name" -> textOrNull(placeholderFor("DISPLAY", "d:" + text));
      default -> value;
    };
  }

  private String placeholderFor(String kind, String original) {
    return placeholderByOriginal.computeIfAbsent(
        kind + ":" + original,
        k -> {
          AtomicInteger counter = counters.computeIfAbsent(kind, x -> new AtomicInteger(0));
          return "<" + kind + "_" + counter.incrementAndGet() + ">";
        });
  }

  private static JsonNode textOrNull(String text) {
    return MAPPER.getNodeFactory().textNode(text);
  }
}
