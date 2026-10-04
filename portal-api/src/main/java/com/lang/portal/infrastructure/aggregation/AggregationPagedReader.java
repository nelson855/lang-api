package com.lang.portal.infrastructure.aggregation;

import com.lang.portal.base.aggregation.AggregationReadBudget;
import com.lang.portal.base.aggregation.ProtectReason;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
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
        throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
      }
      List<T> items = List.copyOf(fetched.items());
      if (frozenTotal == null) {
        frozenTotal = fetched.total();
        if (frozenTotal > budget.remainingRecords()) {
          throw budget.reject(ProtectReason.RECORDS);
        }
      } else if (!frozenTotal.equals(fetched.total())) {
        throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
      }
      if (items.size() > pageSize) {
        throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
      }
      if (!fingerprints.add(items.toString())) {
        throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
      }
      boolean isLast = collected.size() + items.size() >= frozenTotal;
      if (!isLast) {
        if (items.isEmpty()) {
          throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
        }
        if (items.size() != pageSize) {
          throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
        }
      }
      if (collected.size() + items.size() > frozenTotal) {
        throw budget.reject(ProtectReason.INCONSISTENT_PAGE);
      }
      budget.recordPage(items.size());
      collected.addAll(items);
      if (collected.size() >= frozenTotal) {
        return List.copyOf(collected);
      }
      page++;
    }
  }
}
