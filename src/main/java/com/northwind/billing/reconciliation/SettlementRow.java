package com.northwind.billing.reconciliation;

import java.math.BigDecimal;

/**
 * One charge as it appears in the daily settlement file.
 *
 * @param chargeId          our charge identifier
 * @param acquirerReference the acquirer's reference — how finance matches this row to the
 *                          acquirer's own settlement report
 * @param cardType          card network this row is booked under
 * @param gross             amount charged
 * @param interchange       fee we pay the network
 * @param net               what we expect to actually receive
 * @param currency          ISO currency code
 * @param status            charge status
 */
public record SettlementRow(
        String chargeId,
        String acquirerReference,
        String cardType,
        BigDecimal gross,
        BigDecimal interchange,
        BigDecimal net,
        String currency,
        String status
) {
}
