package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountTransactionDtoTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AccountConsumptionRangeDto sampleRange() {
    return new AccountConsumptionRangeDto(
        "2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z", "UTC", "HOUR");
  }

  @Test
  void rangeRequiresAllFields() {
    assertThatThrownBy(() -> new AccountConsumptionRangeDto(null, "2026-09-08T00:00:00Z", "UTC", "HOUR"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountConsumptionRangeDto("2026-09-01T00:00:00Z", null, "UTC", "HOUR"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountConsumptionRangeDto("2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z", null, "HOUR"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountConsumptionRangeDto("2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z", "UTC", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void availableMetricMustHaveValueAndUnitWithoutReason() {
    AccountConsumptionMetric metric =
        AccountConsumptionMetric.available("60", "quota");
    assertThat(metric.value()).isEqualTo("60");
    assertThat(metric.unit()).isEqualTo("quota");
    assertThat(metric.availability()).isEqualTo(AccountAvailability.AVAILABLE);
    assertThat(metric.reasonCode()).isNull();

    assertThatThrownBy(() -> AccountConsumptionMetric.available(null, "quota"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountConsumptionMetric.available("60", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountConsumptionMetric.available("60", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unavailableMetricMustHaveReasonAndNullValue() {
    AccountConsumptionMetric metric =
        AccountConsumptionMetric.unavailable(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
    assertThat(metric.value()).isNull();
    assertThat(metric.unit()).isNull();
    assertThat(metric.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(metric.reasonCode()).isEqualTo(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);

    assertThatThrownBy(() -> new AccountConsumptionMetric(
        "1", "quota", AccountAvailability.UNAVAILABLE, AccountReasonCode.NO_DATA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountConsumptionMetric(
        null, null, AccountAvailability.AVAILABLE, AccountReasonCode.NO_DATA))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountConsumptionMetric(
        null, null, AccountAvailability.PARTIAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moneyMetricAvailableRequiresCurrencyAndValue() {
    AccountMoneyMetric available = AccountMoneyMetric.available("1.50", "USD");
    assertThat(available.value()).isEqualTo("1.50");
    assertThat(available.currency()).isEqualTo("USD");
    assertThat(available.availability()).isEqualTo(AccountAvailability.AVAILABLE);
    assertThat(available.reasonCode()).isNull();

    assertThatThrownBy(() -> AccountMoneyMetric.available(null, "USD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountMoneyMetric.available("1.5", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moneyMetricUnavailableHasNullValueAndCurrency() {
    AccountMoneyMetric unavailable =
        AccountMoneyMetric.unavailable(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
    assertThat(unavailable.value()).isNull();
    assertThat(unavailable.currency()).isNull();
    assertThat(unavailable.availability()).isEqualTo(AccountAvailability.UNAVAILABLE);
    assertThat(unavailable.reasonCode()).isEqualTo(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED);
  }

  @Test
  void moneyMetricForbidsPartialAvailability() {
    assertThatThrownBy(() -> new AccountMoneyMetric(
        "1.5", "USD", AccountAvailability.PARTIAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void summarySerializesStableShape() throws Exception {
    AccountConsumptionSummaryData data =
        new AccountConsumptionSummaryData(
            "p2-2026-09-22-a",
            sampleRange(),
            AccountConsumptionMetric.available("3", "records"),
            AccountConsumptionMetric.available("60", "quota"),
            AccountMoneyMetric.unavailable(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED),
            new AccountSourceCoverageDto(
                AccountTransactionType.CONSUMPTION,
                AccountAvailability.AVAILABLE,
                null));

    JsonNode node = MAPPER.valueToTree(data);
    assertThat(node.get("baselineVersion").asText()).isEqualTo("p2-2026-09-22-a");
    assertThat(node.get("range").get("start").asText()).isEqualTo("2026-09-01T00:00:00Z");
    assertThat(node.get("range").get("end").asText()).isEqualTo("2026-09-08T00:00:00Z");
    assertThat(node.get("range").get("timezone").asText()).isEqualTo("UTC");
    assertThat(node.get("range").get("granularity").asText()).isEqualTo("HOUR");
    assertThat(node.get("recordCount").get("value").asText()).isEqualTo("3");
    assertThat(node.get("recordCount").get("unit").asText()).isEqualTo("records");
    assertThat(node.get("recordCount").get("availability").asText()).isEqualTo("AVAILABLE");
    assertThat(node.get("recordCount").get("reasonCode").isNull()).isTrue();
    assertThat(node.get("quotaTotal").get("value").asText()).isEqualTo("60");
    assertThat(node.get("quotaTotal").get("unit").asText()).isEqualTo("quota");
    assertThat(node.get("moneyTotal").get("value").isNull()).isTrue();
    assertThat(node.get("moneyTotal").get("currency").isNull()).isTrue();
    assertThat(node.get("moneyTotal").get("availability").asText()).isEqualTo("UNAVAILABLE");
    assertThat(node.get("moneyTotal").get("reasonCode").asText())
        .isEqualTo("CURRENCY_CONVERSION_NOT_VERIFIED");
    assertThat(node.get("coverage").get("type").asText()).isEqualTo("CONSUMPTION");
    assertThat(node.get("coverage").get("availability").asText()).isEqualTo("AVAILABLE");
  }

  @Test
  void itemRejectsInconsistentUnitAndCurrencyCombination() {
    assertThatThrownBy(() -> new AccountTransactionItemDto(
        "CONSUMPTION_abc",
        "2026-09-01T00:00:00Z",
        AccountTransactionType.CONSUMPTION,
        AccountTransactionDirection.DEBIT,
        "10",
        AccountTransactionUnit.QUOTA,
        "USD",
        AccountTransactionStatus.SUCCEEDED,
        "model-x",
        "req-1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new AccountTransactionItemDto(
        "CONSUMPTION_abc",
        "2026-09-01T00:00:00Z",
        AccountTransactionType.CONSUMPTION,
        AccountTransactionDirection.DEBIT,
        "10",
        AccountTransactionUnit.CURRENCY,
        null,
        AccountTransactionStatus.SUCCEEDED,
        "model-x",
        "req-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itemRejectsNegativeOrBlankAmount() {
    assertThatThrownBy(() -> new AccountTransactionItemDto(
        "CONSUMPTION_abc",
        "2026-09-01T00:00:00Z",
        AccountTransactionType.CONSUMPTION,
        AccountTransactionDirection.DEBIT,
        "-1",
        AccountTransactionUnit.QUOTA,
        null,
        AccountTransactionStatus.SUCCEEDED,
        null,
        null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountTransactionItemDto(
        "CONSUMPTION_abc",
        "2026-09-01T00:00:00Z",
        AccountTransactionType.CONSUMPTION,
        AccountTransactionDirection.DEBIT,
        " ",
        AccountTransactionUnit.QUOTA,
        null,
        AccountTransactionStatus.SUCCEEDED,
        null,
        null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itemSerializesStableShape() throws Exception {
    AccountTransactionItemDto item =
        new AccountTransactionItemDto(
            "CONSUMPTION_0123456789abcdef",
            "2026-09-01T12:00:00Z",
            AccountTransactionType.CONSUMPTION,
            AccountTransactionDirection.DEBIT,
            "60",
            AccountTransactionUnit.QUOTA,
            null,
            AccountTransactionStatus.SUCCEEDED,
            "gpt-4o",
            "req-abc");
    JsonNode node = MAPPER.valueToTree(item);
    assertThat(node.get("transactionId").asText()).isEqualTo("CONSUMPTION_0123456789abcdef");
    assertThat(node.get("occurredAt").asText()).isEqualTo("2026-09-01T12:00:00Z");
    assertThat(node.get("type").asText()).isEqualTo("CONSUMPTION");
    assertThat(node.get("direction").asText()).isEqualTo("DEBIT");
    assertThat(node.get("amount").asText()).isEqualTo("60");
    assertThat(node.get("unit").asText()).isEqualTo("QUOTA");
    assertThat(node.get("currency").isNull()).isTrue();
    assertThat(node.get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(node.get("remark").asText()).isEqualTo("gpt-4o");
    assertThat(node.get("referenceId").asText()).isEqualTo("req-abc");
  }

  @Test
  void transactionsResponseSerializesStableShape() throws Exception {
    AccountTransactionsData data =
        new AccountTransactionsData(
            "p2-2026-09-22-a",
            sampleRange(),
            AccountTransactionType.CONSUMPTION,
            1,
            20,
            2L,
            AccountAvailability.PARTIAL,
            null,
            List.of(
                new AccountSourceCoverageDto(
                    AccountTransactionType.TOPUP,
                    AccountAvailability.UNAVAILABLE,
                    AccountReasonCode.BASELINE_NOT_VERIFIED),
                new AccountSourceCoverageDto(
                    AccountTransactionType.CONSUMPTION,
                    AccountAvailability.AVAILABLE,
                    null),
                new AccountSourceCoverageDto(
                    AccountTransactionType.REFUND,
                    AccountAvailability.UNAVAILABLE,
                    AccountReasonCode.SOURCE_NOT_AVAILABLE)),
            List.of(
                new AccountTransactionItemDto(
                    "CONSUMPTION_a",
                    "2026-09-01T12:00:00Z",
                    AccountTransactionType.CONSUMPTION,
                    AccountTransactionDirection.DEBIT,
                    "30",
                    AccountTransactionUnit.QUOTA,
                    null,
                    AccountTransactionStatus.SUCCEEDED,
                    "m",
                    "r1"),
                new AccountTransactionItemDto(
                    "CONSUMPTION_b",
                    "2026-09-01T11:00:00Z",
                    AccountTransactionType.CONSUMPTION,
                    AccountTransactionDirection.DEBIT,
                    "30",
                    AccountTransactionUnit.QUOTA,
                    null,
                    AccountTransactionStatus.SUCCEEDED,
                    "m",
                    "r2")));

    JsonNode node = MAPPER.valueToTree(data);
    assertThat(node.get("baselineVersion").asText()).isEqualTo("p2-2026-09-22-a");
    assertThat(node.get("type").asText()).isEqualTo("CONSUMPTION");
    assertThat(node.get("page").asInt()).isEqualTo(1);
    assertThat(node.get("pageSize").asInt()).isEqualTo(20);
    assertThat(node.get("total").asLong()).isEqualTo(2L);
    assertThat(node.get("availability").asText()).isEqualTo("PARTIAL");
    assertThat(node.get("reasonCode").isNull()).isTrue();
    assertThat(node.get("coverage")).hasSize(3);
    assertThat(node.get("coverage").get(0).get("type").asText()).isEqualTo("TOPUP");
    assertThat(node.get("coverage").get(1).get("type").asText()).isEqualTo("CONSUMPTION");
    assertThat(node.get("coverage").get(2).get("type").asText()).isEqualTo("REFUND");
    assertThat(node.get("items")).hasSize(2);
  }

  @Test
  void transactionsResponseTypeMayBeNullWhenAll() throws Exception {
    AccountTransactionsData data =
        new AccountTransactionsData(
            "p2-2026-09-22-a",
            sampleRange(),
            null,
            1,
            20,
            0L,
            AccountAvailability.PARTIAL,
            null,
            List.of(),
            List.of());
    JsonNode node = MAPPER.valueToTree(data);
    assertThat(node.get("type").isNull()).isTrue();
  }
}
