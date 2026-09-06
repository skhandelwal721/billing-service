package com.northwind.billing.reconciliation;

import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.charge.ChargeResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementFileBuilderTest {

    private final SettlementFileBuilder builder = new SettlementFileBuilder(new InterchangeRates());

    @Test
    void groupsRowsByCardNetwork() {
        SettlementFile file = builder.build("2026-09-01", List.of(
                charge("chg_1", "VISA", "249.00", "49.80"),
                charge("chg_2", "VISA", "100.00", "20.00"),
                charge("chg_3", "MASTERCARD", "18.50", "3.70")));

        assertEquals(3, file.rowCount());
        assertEquals(2, file.sections().get("VISA").size());
        assertEquals(1, file.sections().get("MASTERCARD").size());
        assertTrue(file.exceptions().isEmpty());
    }

    @Test
    void deductsInterchangeFromGrossToGetNet() {
        SettlementFile file = builder.build("2026-09-01", List.of(
                charge("chg_1", "VISA", "1000.00", "200.00")));

        SettlementRow row = file.sections().get("VISA").get(0);
        assertEquals(new BigDecimal("1200.00"), row.gross());
        assertEquals(new BigDecimal("3.60"), row.interchange());
        assertEquals(new BigDecimal("1196.40"), row.net());
    }

    /**
     * The grouping key is the {@code cardType} field of the published charge response, and the
     * sections it produces are booked against real settlement accounts. Anything that is not a
     * network name is money we cannot book, so it must land in exceptions rather than create a
     * section nobody will pay us for.
     */
    @Test
    void unratedCardTypeBecomesAnExceptionRatherThanASection() {
        SettlementFile file = builder.build("2026-09-01", List.of(
                charge("chg_1", "CHARGE_CARD", "249.00", "49.80")));

        assertEquals(0, file.rowCount());
        assertEquals(1, file.exceptions().size());
        assertTrue(file.exceptions().get(0).contains("no agreed interchange rate"));
    }

    /**
     * {@code subtotal + tax == total} is the only integrity check between a mispriced charge
     * and the general ledger. A row where the total carries something the subtotal and tax do
     * not account for is an escalation, not a rounding difference.
     */
    @Test
    void rowThatDoesNotBalanceBecomesAnException() {
        ChargeResponse unbalanced = new ChargeResponse(
                "chg_9",
                "inv-1001",
                new BigDecimal("249.00"),
                new BigDecimal("0.00"),
                new BigDecimal("49.80"),
                new BigDecimal("302.53"),
                "GBP",
                "VISA",
                "wp_4f8a21c7",
                "CHARGED");

        SettlementFile file = builder.build("2026-09-01", List.of(unbalanced));

        assertEquals(0, file.rowCount());
        assertTrue(file.exceptions().get(0).contains("does not balance"));
    }

    /**
     * Every network we can charge has to be bookable, otherwise a charge we take never reaches
     * the settlement file. Enumerates {@link CardNetwork#values()} deliberately: adding a
     * network without an interchange rate should fail here, before finance finds it.
     */
    @Test
    void everyNetworkIsBookableInTheSettlementFile() {
        InterchangeRates rates = new InterchangeRates();
        for (CardNetwork network : CardNetwork.values()) {
            assertTrue(rates.isRated(network.code()),
                    "no interchange rate agreed for " + network.code()
                            + " — charges on this network cannot be settled");
        }
    }

    private static ChargeResponse charge(String chargeId, String cardType, String subtotal, String tax) {
        BigDecimal net = new BigDecimal(subtotal);
        BigDecimal taxAmount = new BigDecimal(tax);
        return new ChargeResponse(
                chargeId,
                "inv-1001",
                net,
                BigDecimal.ZERO.setScale(2),
                taxAmount,
                net.add(taxAmount),
                "GBP",
                cardType,
                "wp_" + chargeId,
                "CHARGED");
    }
}
