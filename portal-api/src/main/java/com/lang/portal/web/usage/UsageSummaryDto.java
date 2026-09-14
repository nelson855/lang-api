package com.lang.portal.web.usage;

public record UsageSummaryDto(
    String quota, String amount, String currency, long rpm, long tpm, int rateWindowSeconds) {}
