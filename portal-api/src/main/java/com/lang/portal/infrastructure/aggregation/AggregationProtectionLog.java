package com.lang.portal.infrastructure.aggregation;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationProtectionLog {

  private static final Logger LOG = LoggerFactory.getLogger(AggregationProtectionLog.class);

  private AggregationProtectionLog() {}

  public static void warn(String requestId, String operation, ProtectReason reason) {
    Objects.requireNonNull(requestId, "requestId 不能为空");
    Objects.requireNonNull(operation, "操作命名空间不能为空");
    Objects.requireNonNull(reason, "拒绝原因不能为空");
    LOG.warn(
        "event=aggregation_protection op={} reason={} requestId={}",
        operation,
        reason.tag(),
        requestId);
  }
}
