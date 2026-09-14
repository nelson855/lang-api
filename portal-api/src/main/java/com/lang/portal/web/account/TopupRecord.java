package com.lang.portal.web.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TopupRecord(
    String orderId,
    String requestedAmount,
    String currency,
    String paymentMethod,
    String status,
    String createdAt,
    String completedAt) {}
