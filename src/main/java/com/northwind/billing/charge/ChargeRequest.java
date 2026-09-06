package com.northwind.billing.charge;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * A request to charge an invoice. The amount is never supplied by the caller — it comes from
 * the invoice, and tax is resolved server-side.
 *
 * <p>{@code promotionalAdjustment} is the one exception, and it is a deduction, not an amount.
 * See the component documentation below before sending it.
 */
public record ChargeRequest(

        @NotBlank(message = "cardNumber is required")
        String cardNumber,

        @NotBlank(message = "currency is required")
        String currency,

        String billingPostcode,

        /**
         * Optional promotional deduction, taken off the invoice subtotal before tax.
         *
         * <p><strong>An absolute amount, in the charge currency.</strong> Not a percentage, not
         * a rate, not basis points. 24.90 means twenty-four pounds ninety off a sterling
         * invoice. We deliberately do not accept a rate: the caller knows the basis it applied
         * and we do not, so a rate here would mean two services computing the same money figure
         * from different inputs and disagreeing quietly.
         *
         * <p>Must not exceed the subtotal. A deduction larger than the invoice is rejected
         * rather than clamped or charged as a negative — see {@code ChargeService}.
         *
         * <p>Null or zero means no promotion.
         */
        BigDecimal promotionalAdjustment
) {
}
