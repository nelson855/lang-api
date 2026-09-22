package com.lang.portal.infrastructure.aggregation;

import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AggregationPagedReader {

  public record Page<T>(int total, List<T> items) {
    public Page {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public interface PageFetcher<T> {
    Page<T> fetch(int page);
  }

  private AggregationPagedReader() {}

  public static <T> List<T> readAll(
      AggregationReadBudget budget, int pageSize, PageFetcher<T> fetcher) {
    if (budget == null || fetcher == null) {
      throw new IllegalArgumentException("预算与分页源不能为空");
    }
    if (pageSize < 1) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "分页大小必须为正数");
    }
    List<T> collected = new ArrayList<>();
    Set<String> fingerprints = new HashSet<>();
    Integer frozenTotal = null;
    int page = 1;
    while (true) {
      budget.checkBeforeCall();
      Page<T> fetched = fetcher.fetch(page);
      if (fetched == null || fetched.items() == null || fetched.total() < 0) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      List<T> items = List.copyOf(fetched.items());
      if (frozenTotal == null) {
        frozenTotal = fetched.total();
        if (frozenTotal > budget.remainingRecords()) {
          throw new PortalException(
              PortalErrorCode.INVALID_ARGUMENT, "聚合数据量超过保护上限，请缩小时间范围后重试");
        }
      } else if (!frozenTotal.equals(fetched.total())) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      if (items.size() > pageSize) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      if (!fingerprints.add(items.toString())) {
        throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
      }
      boolean isLast = collected.size() + items.size() >= frozenTotal;
      if (!isLast) {
        if (items.isEmpty()) {
          throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
        }
        if (items.size() != pageSize) {
          throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
        }
      }
      budget.recordPage(items.size());
      collected.addAll(items);
      if (collected.size() >= frozenTotal) {
        if (collected.size() > frozenTotal) {
          throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
        }
        return List.copyOf(collected);
      }
      page++;
    }
  }
}
