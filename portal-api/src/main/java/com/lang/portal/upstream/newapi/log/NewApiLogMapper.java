package com.lang.portal.upstream.newapi.log;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class NewApiLogMapper {
  private NewApiLogMapper() {}

  public static NewApiLogPage mapPage(NewApiLogPageRaw raw, NewApiLogResult expected) {
    if (raw == null || expected == null) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    if (raw.total() < 0) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    List<NewApiLogRecord> records = new ArrayList<>();
    for (NewApiLogEntryRaw entry : raw.items()) {
      records.add(mapEntry(entry, expected));
    }
    return new NewApiLogPage(raw.total(), List.copyOf(records));
  }

  private static NewApiLogRecord mapEntry(NewApiLogEntryRaw entry, NewApiLogResult expected) {
    try {
      if (entry == null
          || entry.createdTime() == null
          || entry.createdTime() < 0
          || entry.type() == null
          || entry.type() != expected.upstreamType()
          || entry.quota() == null
          || entry.quota() < 0
          || entry.promptTokens() == null
          || entry.promptTokens() < 0
          || entry.completionTokens() == null
          || entry.completionTokens() < 0
          || entry.useTime() == null
          || entry.useTime() < 0
          || entry.useTime() > Long.MAX_VALUE / 1000L
          || (entry.tokenId() != null && entry.tokenId() < 0)) {
        throw new IllegalArgumentException("日志条目非法");
      }
      return new NewApiLogRecord(
          Instant.ofEpochSecond(entry.createdTime()),
          blankToNull(entry.tokenName()),
          blankToNull(entry.modelName()),
          expected,
          entry.promptTokens(),
          entry.completionTokens(),
          entry.useTime() * 1000L,
          Boolean.TRUE.equals(entry.stream()),
          entry.quota(),
          blankToNull(entry.requestId()),
          entry.tokenId());
    } catch (UpstreamException e) {
      throw e;
    } catch (Exception e) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
