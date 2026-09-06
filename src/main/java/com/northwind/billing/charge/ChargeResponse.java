package com.northwind.billing.charge;

import java.math.BigDecimal;

/**
 * Result of charging an invoice. This is a published contract with external consumers —
 * {@code order-service} and the finance reconciliation export both parse it.
 *
 * <p>{@code cardType} now carries the funding type — {@code "CREDIT"} or
 * {@code "CHARGE_CARD"}. Calling a network a "card type" was always a bit loose, and with
 * Amex in the mix the distinction actually matters, so the network has moved to the clearer
 * {@code cardNetwork} field. No information is lost: anything that needs the network can read
 * it from there.
 *
 * @param chargeId          our identifier for the charge
 * @param invoiceId         invoice that was charged
 * @param subtotal          invoice amount before tax
 * @param surcharge         network surcharge, currently Amex only
 * @param tax               tax from tax-service
 * @param total             amount actually charged
 * @param currency          ISO currency code
 * @param cardType          funding type — CREDIT or CHARGE_CARD
 * @param cardNetwork       card network — VISA, MASTERCARD or AMEX
 * @param acquirerReference the acquirer's reference, needed for settlement and refunds
 * @param status            CHARGED or DECLINED
 */
public record ChargeResponse(
        String chargeId,
        String invoiceId,
        BigDecimal subtotal,
        BigDecimal surcharge,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String cardType,
        String cardNetwork,
        String acquirerReference,
        String status
) {
}
