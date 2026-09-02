package com.northwind.billing.refund;

import java.math.BigDecimal;

/**
 * Result of a refund. Finance matches this against the acquirer settlement report by
 * {@code acquirerReference}, the same way it matches charges.
 */
public record RefundReceipt(
        String acquirerReference,
        String cardType,
        BigDecimal amount,
        String status
) {
}
