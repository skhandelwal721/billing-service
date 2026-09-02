package com.northwind.billing.refund;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * A request to refund a charge.
 *
 * <p>{@code acquirerReference} and {@code cardType} are both copied straight off the charge
 * response the caller received — see {@code docs/api/charge.md}. We do not re-derive them,
 * because the refund may be raised days later by finance from the settlement file rather than
 * by whoever took the charge.
 *
 * @param acquirerReference the reference from the charge response — the only handle on the money
 * @param cardType          the card network from the charge response, e.g. {@code "VISA"}
 * @param amount            amount to refund, up to the amount charged
 * @param daysSinceCharge   how long ago the charge was taken, for the scheme refund window
 */
public record RefundRequest(

        @NotBlank(message = "acquirerReference is required")
        String acquirerReference,

        @NotBlank(message = "cardType is required")
        String cardType,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @PositiveOrZero(message = "daysSinceCharge must not be negative")
        int daysSinceCharge
) {
}
