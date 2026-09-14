package com.lang.portal.upstream.newapi.pricing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import com.lang.portal.web.catalog.PricingSnapshotProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiPricingClient implements com.lang.portal.web.catalog.PricingSnapshotProvider {

  private final NewApiExchange exchange;
  private final ObjectMapper mapper;

  public NewApiPricingClient(NewApiExchange exchange) {
    this.exchange = exchange;
    this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Override
  public com.lang.portal.web.catalog.PricingSnapshotProvider.Snapshot fetchSnapshot() {
    NewApiOperation op = new NewApiOperation("pricing-get", HttpMethod.GET, "/api/pricing", false);
    NewApiRawResponse<JsonNode> raw = exchange.executeRaw(
        op, null, Map.of(), new TypeReference<NewApiEnvelope<JsonNode>>() {});
    if (raw.status() < 200 || raw.status() >= 300 || !raw.success() || raw.data() == null) {
      throw exchange.failure(raw);
    }
    return parse(raw.data());
  }

  PricingSnapshotProvider.Snapshot parse(JsonNode data) {
    String pricingVersion = null;
    List<NewApiPricingEntry> entries = new ArrayList<>();
    List<NewApiPricingVendor> vendors = new ArrayList<>();
    try {
      if (data.isArray()) {
        for (JsonNode n : data) {
          entries.add(mapper.treeToValue(n, NewApiPricingEntry.class));
        }
      } else if (data.isObject()) {
        if (data.has("pricing_version") && data.get("pricing_version").isTextual()) {
          pricingVersion = data.get("pricing_version").asText();
        }
        JsonNode models = firstPresent(data, "data", "models", "items");
        if (models != null && models.isArray()) {
          for (JsonNode n : models) {
            entries.add(mapper.treeToValue(n, NewApiPricingEntry.class));
          }
        }
        JsonNode vendorNode = firstPresent(data, "vendors", "vendor", "providers");
        if (vendorNode != null && vendorNode.isArray()) {
          for (JsonNode n : vendorNode) {
            vendors.add(mapper.treeToValue(n, NewApiPricingVendor.class));
          }
        }
        if ((models == null) && entries.isEmpty()) {
          throw new IllegalArgumentException("定价数据结构非法");
        }
      } else {
        throw new IllegalArgumentException("定价数据结构非法");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return new PricingSnapshotProvider.Snapshot(entries, vendors, pricingVersion);
  }

  private JsonNode firstPresent(JsonNode node, String... keys) {
    for (String k : keys) {
      if (node.has(k)) {
        return node.get(k);
      }
    }
    return null;
  }
}
