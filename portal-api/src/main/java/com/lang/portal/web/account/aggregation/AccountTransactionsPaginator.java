package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AccountTransactionsPaginator {

  private AccountTransactionsPaginator() {}

  public static AccountTransactionsData paginate(
      ConsumptionSnapshot snapshot,
      AggregationQueryContext context,
      long userId,
      AccountTransactionType requestedType,
      int page,
      int pageSize,
      int maxRecords) {
    Objects.requireNonNull(snapshot, "消费快照不能为空");
    Objects.requireNonNull(context, "查询上下文不能为空");
    if (page < 1 || pageSize < 1) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT, "分页参数必须从 1 开始");
    }
    long endOffset = (long) page * (long) pageSize;
    if (endOffset > maxRecords) {
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "分页窗口超过当前保护上限，请缩小页码或页大小");
    }

    List<AccountTransactionItemDto> projected;
    AccountAvailability consumptionAvailability;
    AccountReasonCode consumptionReason;
    if (requestedType == null || requestedType == AccountTransactionType.CONSUMPTION) {
      projected = new ArrayList<>(ConsumptionTransactionProjector.project(snapshot, userId));
      consumptionAvailability =
          snapshot.missingReferenceCount() == 0
              ? AccountAvailability.AVAILABLE
              : AccountAvailability.PARTIAL;
      consumptionReason =
          snapshot.missingReferenceCount() == 0
              ? null
              : AccountReasonCode.MISSING_STABLE_REFERENCE;
    } else {
      projected = new ArrayList<>();
      consumptionAvailability = AccountAvailability.UNAVAILABLE;
      consumptionReason = AccountReasonCode.SOURCE_NOT_AVAILABLE;
    }

    projected.sort(
        Comparator.comparing(AccountTransactionItemDto::occurredAt)
            .reversed()
            .thenComparing(AccountTransactionItemDto::transactionId));

    int total = projected.size();
    int fromIndex = (int) Math.min((long) (page - 1) * pageSize, total);
    int toIndex = (int) Math.min(fromIndex + (long) pageSize, total);
    List<AccountTransactionItemDto> items =
        fromIndex >= total ? List.of() : projected.subList(fromIndex, toIndex);

    List<AccountSourceCoverageDto> coverage = buildCoverage(
        requestedType, consumptionAvailability, consumptionReason);
    AccountAvailability overall = computeOverall(requestedType, consumptionAvailability);
    AccountReasonCode overallReason = computeOverallReason(requestedType, consumptionReason);

    AccountConsumptionRangeDto range =
        new AccountConsumptionRangeDto(
            context.start().toString(),
            context.end().toString(),
            context.zone().getId(),
            context.granularity().name());

    return new AccountTransactionsData(
        context.baselineVersion(),
        range,
        requestedType,
        page,
        pageSize,
        total,
        overall,
        overallReason,
        coverage,
        items);
  }

  private static List<AccountSourceCoverageDto> buildCoverage(
      AccountTransactionType requestedType,
      AccountAvailability consumptionAvailability,
      AccountReasonCode consumptionReason) {
    if (requestedType == AccountTransactionType.CONSUMPTION) {
      return List.of(
          new AccountSourceCoverageDto(
              AccountTransactionType.CONSUMPTION, consumptionAvailability, consumptionReason));
    }
    if (requestedType == AccountTransactionType.TOPUP) {
      return List.of(
          new AccountSourceCoverageDto(
              AccountTransactionType.TOPUP,
              AccountAvailability.UNAVAILABLE,
              AccountReasonCode.BASELINE_NOT_VERIFIED));
    }
    if (requestedType == AccountTransactionType.REFUND) {
      return List.of(
          new AccountSourceCoverageDto(
              AccountTransactionType.REFUND,
              AccountAvailability.UNAVAILABLE,
              AccountReasonCode.SOURCE_NOT_AVAILABLE));
    }
    return List.of(
        new AccountSourceCoverageDto(
            AccountTransactionType.TOPUP,
            AccountAvailability.UNAVAILABLE,
            AccountReasonCode.BASELINE_NOT_VERIFIED),
        new AccountSourceCoverageDto(
            AccountTransactionType.CONSUMPTION, consumptionAvailability, consumptionReason),
        new AccountSourceCoverageDto(
            AccountTransactionType.REFUND,
            AccountAvailability.UNAVAILABLE,
            AccountReasonCode.SOURCE_NOT_AVAILABLE));
  }

  private static AccountAvailability computeOverall(
      AccountTransactionType requestedType, AccountAvailability consumptionAvailability) {
    if (requestedType == null) {
      return consumptionAvailability == AccountAvailability.AVAILABLE
          ? AccountAvailability.PARTIAL
          : consumptionAvailability == AccountAvailability.PARTIAL
              ? AccountAvailability.PARTIAL
              : AccountAvailability.UNAVAILABLE;
    }
    return switch (requestedType) {
      case CONSUMPTION -> consumptionAvailability;
      case TOPUP, REFUND -> AccountAvailability.UNAVAILABLE;
    };
  }

  private static AccountReasonCode computeOverallReason(
      AccountTransactionType requestedType, AccountReasonCode consumptionReason) {
    if (requestedType == null) {
      return null;
    }
    return switch (requestedType) {
      case CONSUMPTION -> consumptionReason;
      case TOPUP -> AccountReasonCode.BASELINE_NOT_VERIFIED;
      case REFUND -> AccountReasonCode.SOURCE_NOT_AVAILABLE;
    };
  }
}
