package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountTransactionWhitelistTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void summaryAndItemsNeverExposeSensitiveOrUpstreamFields() throws Exception {
    AccountConsumptionSummaryData summary =
        new AccountConsumptionSummaryData(
            "p2-2026-09-22-a",
            new AccountConsumptionRangeDto(
                "2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z", "UTC", "HOUR"),
            AccountConsumptionMetric.available("1", "records"),
            AccountConsumptionMetric.available("10", "quota"),
            AccountMoneyMetric.unavailable(AccountReasonCode.CURRENCY_CONVERSION_NOT_VERIFIED),
            new AccountSourceCoverageDto(
                AccountTransactionType.CONSUMPTION, AccountAvailability.AVAILABLE, null));

    JsonNode summaryJson = MAPPER.valueToTree(summary);
    assertNoSensitiveFields(summaryJson);

    AccountTransactionItemDto item =
        new AccountTransactionItemDto(
            "CONSUMPTION_x",
            "2026-09-01T00:00:00Z",
            AccountTransactionType.CONSUMPTION,
            AccountTransactionDirection.DEBIT,
            "10",
            AccountTransactionUnit.QUOTA,
            null,
            AccountTransactionStatus.SUCCEEDED,
            "m",
            "r");
    JsonNode itemJson = MAPPER.valueToTree(item);
    assertNoSensitiveFields(itemJson);
  }

  private static void assertNoSensitiveFields(JsonNode node) {
    List<String> forbidden =
        List.of(
            "userId", "username", "user_id",
            "tokenId", "token_id", "token", "key", "apiKey", "api_key",
            "keyName", "key_name",
            "channel", "channelId", "channel_id",
            "node", "provider", "vendor",
            "callback", "merchant",
            "rawLog", "raw", "upstream",
            "quota per usd", "quotaPerUsd");
    String json = node.toString();
    for (String field : forbidden) {
      assertThat(json).doesNotContain("\"" + field + "\"");
    }
  }
}
