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
      throw new UpstreamException(PortalErrorCode.UPSTREAM_TIMEOUT);
    }
    if (usedPages >= maxPages) {
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "聚合数据量超过保护上限，请缩小时间范围后重试");
    }
  }

  public synchronized void recordPage(int recordCount) {
    if (recordCount < 0) {
      throw new IllegalArgumentException("记录数不能为负");
    }
    usedPages++;
    usedRecords += recordCount;
    if (usedPages > maxPages || usedRecords > maxRecords) {
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "聚合数据量超过保护上限，请缩小时间范围后重试");
    }
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
