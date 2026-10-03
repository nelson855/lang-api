package com.lang.portal.base.aggregation;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AggregationReadBudget {

  private final int maxPages;
  private final int maxRecords;
  private final Instant deadline;
  private final Clock clock;
  private ProtectReason protectionReason;
  private int usedPages;
  private int usedRecords;

  public AggregationReadBudget(int maxPages, int maxRecords, Instant deadline, Clock clock) {
    if (maxPages < 1 || maxRecords < 1) {
      throw new IllegalArgumentException("页数与记录数上限必须为正数");
    }
    this.maxPages = maxPages;
    this.maxRecords = maxRecords;
    this.deadline = Objects.requireNonNull(deadline, "截止时间不能为空");
    this.clock = Objects.requireNonNull(clock, "时钟不能为空");
  }

  public synchronized void checkBeforeCall() {
    if (!clock.instant().isBefore(deadline)) {
      throw reject(ProtectReason.DEADLINE);
    }
    if (usedPages >= maxPages) {
      throw reject(ProtectReason.PAGES);
    }
  }

  public synchronized void recordPage(int recordCount) {
    if (recordCount < 0) {
      throw new IllegalArgumentException("记录数不能为负");
    }
    if (usedPages >= maxPages) {
      throw reject(ProtectReason.PAGES);
    }
    if (recordCount > remainingRecords()) {
      throw reject(ProtectReason.RECORDS);
    }
    usedPages++;
    usedRecords += recordCount;
  }

  /** 保留拒绝原因，供一次来源读取的收口处计数；异常类型及错误码保持原契约。 */
  public synchronized PortalException reject(ProtectReason reason) {
    protectionReason = reason;
    return switch (reason) {
      case DEADLINE, SINGLE_TIMEOUT -> new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
      case INCONSISTENT_PAGE -> new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      case RANGE -> new PortalException(PortalErrorCode.INVALID_ARGUMENT,
          "实时日志扫描范围超过当前证据允许上限，请缩小时间范围后重试");
      case PAGES, RECORDS -> new PortalException(PortalErrorCode.INVALID_ARGUMENT,
          "聚合数据量超过保护上限，请缩小时间范围后重试");
    };
  }

  public synchronized ProtectReason protectionReason() {
    return protectionReason;
  }

  public synchronized int remainingRecords() {
    return Math.max(0, maxRecords - usedRecords);
  }

  public int maxPages() {
    return maxPages;
  }

  public int maxRecords() {
    return maxRecords;
  }

  public Instant deadline() {
    return deadline;
  }

  public synchronized int usedPages() {
    return usedPages;
  }

  public synchronized int usedRecords() {
    return usedRecords;
  }
}
