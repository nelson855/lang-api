package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConsumptionTransactionProjector {

  private ConsumptionTransactionProjector() {}

  public static List<AccountTransactionItemDto> project(ConsumptionSnapshot snapshot, long userId) {
    Objects.requireNonNull(snapshot, "消费快照不能为空");
    if (userId <= 0) {
      throw new IllegalArgumentException("用户 ID 必须为正数");
    }
    List<AggregationLogRecord> projectable = snapshot.projectable();
    List<AccountTransactionItemDto> items = new ArrayList<>(projectable.size());
    Set<String> seen = new HashSet<>();
    for (AggregationLogRecord record : projectable) {
      String id = StableTransactionId.consumption(userId, record.requestId());
      if (!seen.add(id)) {
        throw new IllegalStateException("检测到重复 transactionId，来源完整性冲突");
      }
      items.add(
          new AccountTransactionItemDto(
              id,
              record.occurredAt().toString(),
              AccountTransactionType.CONSUMPTION,
              AccountTransactionDirection.DEBIT,
              Long.toString(record.rawQuota()),
              AccountTransactionUnit.QUOTA,
              null,
              AccountTransactionStatus.SUCCEEDED,
              record.model(),
              record.requestId()));
    }
    return List.copyOf(items);
  }
}
