package com.lang.portal.web.account.aggregation;

import com.lang.portal.base.aggregation.AggregationLogRecord;
import com.lang.portal.base.aggregation.AggregationQueryContext;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class StubConsumptionSnapshotService extends AccountConsumptionSnapshotService {

  private final AtomicInteger snapshotCalls = new AtomicInteger();
  private List<AggregationLogRecord> stubbed = List.of();

  StubConsumptionSnapshotService() {
    super(null, new com.lang.portal.config.PortalCommonProperties(), null);
  }

  void stub(List<AggregationLogRecord> records) {
    this.stubbed = records;
  }

  int snapshotCalls() {
    return snapshotCalls.get();
  }

  @Override
  public ConsumptionSnapshot loadSnapshot(
      NewApiSession session, AggregationQueryContext context, Clock clock) {
    snapshotCalls.incrementAndGet();
    return ConsumptionSnapshot.from(stubbed);
  }

  @Override
  public ConsumptionSnapshot loadSnapshotForTransactions(
      NewApiSession session,
      AggregationQueryContext context,
      AccountTransactionType requestedType,
      int page,
      int pageSize,
      Clock clock) {
    snapshotCalls.incrementAndGet();
    return ConsumptionSnapshot.from(stubbed);
  }
}
