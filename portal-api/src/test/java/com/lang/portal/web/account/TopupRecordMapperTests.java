package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.UpstreamException;
import org.junit.jupiter.api.Test;

class TopupRecordMapperTests {

  @Test
  void pendingWithZeroCompleteTimeMapsToPendingAndNull() {
    TopupRecord record = TopupRecordMapper.from("ORD-1", "10", "alipay", "pending", 1700000000L, 0L);

    assertThat(record.status()).isEqualTo("PENDING");
    assertThat(record.completedAt()).isNull();
    assertThat(record.orderId()).isEqualTo("ORD-1");
    assertThat(record.currency()).isEqualTo("USD");
  }

  @Test
  void successFailedExpiredMapToStableStatuses() {
    assertThat(TopupRecordMapper.from("O1", "20", "wxpay", "success", 1700000000L, 1700000100L).status())
        .isEqualTo("SUCCEEDED");
    assertThat(TopupRecordMapper.from("O2", "20", "wxpay", "failed", 1700000000L, 1700000100L).status())
        .isEqualTo("FAILED");
    assertThat(TopupRecordMapper.from("O3", "20", "wxpay", "expired", 1700000000L, 1700000100L).status())
        .isEqualTo("EXPIRED");
  }

  @Test
  void unknownPaymentMethodConvergesToOther() {
    TopupRecord record = TopupRecordMapper.from("O4", "20", "mystery-pay", "success", 1700000000L, 1700000100L);

    assertThat(record.paymentMethod()).isEqualTo("OTHER");
  }

  @Test
  void unknownStatusOrNegativeAmountFailsWholePage() {
    assertThatThrownBy(() -> TopupRecordMapper.from("O5", "20", "alipay", "weird", 1700000000L, 1700000100L))
        .isInstanceOf(UpstreamException.class);
    assertThatThrownBy(() -> TopupRecordMapper.from("O6", "-5", "alipay", "success", 1700000000L, 1700000100L))
        .isInstanceOf(UpstreamException.class);
    assertThatThrownBy(() -> TopupRecordMapper.from("", "20", "alipay", "success", 1700000000L, 1700000100L))
        .isInstanceOf(UpstreamException.class);
  }
}
