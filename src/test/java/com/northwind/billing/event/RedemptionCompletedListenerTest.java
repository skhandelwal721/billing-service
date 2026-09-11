package com.northwind.billing.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.reconciliation.InterchangeRates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionCompletedListenerTest {

    private final RedemptionCompletedListener listener =
            new RedemptionCompletedListener(new InterchangeRates());

    @Test
    void booksTheExpectedRebateAgainstTheFundingNetwork() {
        listener.onRedemptionCompleted(event("rdm_1", "NW-VISA-10", "VISA", "24.90", "REDEEMED"));

        // 24.90 * 0.0030 = 0.0747 -> 0.07
        assertEquals(new BigDecimal("0.07"), listener.expectedRebateFor(CardNetwork.VISA));
    }

    @Test
    void accumulatesRebatePerNetwork() {
        listener.onRedemptionCompleted(event("rdm_1", "NW-VISA-10", "VISA", "100.00", "REDEEMED"));
        listener.onRedemptionCompleted(event("rdm_2", "NW-VISA-10", "VISA", "100.00", "REDEEMED"));

        assertEquals(new BigDecimal("0.60"), listener.expectedRebateFor(CardNetwork.VISA));
    }

    /** At-least-once delivery. A redelivery must not double-book the rebate. */
    @Test
    void isIdempotentOnRedemptionId() {
        listener.onRedemptionCompleted(event("rdm_1", "NW-VISA-10", "VISA", "100.00", "REDEEMED"));
        listener.onRedemptionCompleted(event("rdm_1", "NW-VISA-10", "VISA", "100.00", "REDEEMED"));

        assertEquals(new BigDecimal("0.30"), listener.expectedRebateFor(CardNetwork.VISA));
    }

    /**
     * The deduplication key is the whole idempotency mechanism. An event without one cannot be
     * distinguished from a redelivery, so it is refused rather than booked.
     *
     * <p>If this test is ever changed to accept a missing {@code redemptionId}, the rebate
     * ledger stops being idempotent and a redelivery becomes a misstated receivable.
     */
    @Test
    void refusesAnEventThatCannotBeDeduplicated() {
        RedemptionCompletedListener.UndeduplicableEventException e = assertThrows(
                RedemptionCompletedListener.UndeduplicableEventException.class,
                () -> listener.onRedemptionCompleted(
                        event(null, "NW-VISA-10", "VISA", "24.90", "REDEEMED")));

        assertTrue(e.getMessage().contains("cannot be deduplicated"));
    }

    @Test
    void doesNotBookARebateForANonTerminalRedemption() {
        listener.onRedemptionCompleted(event("rdm_1", "NW-VISA-10", "VISA", "24.90", "PENDING"));

        assertEquals(new BigDecimal("0.00"), listener.expectedRebateFor(CardNetwork.VISA));
    }

    /**
     * {@code fundingNetwork} is a closed set and we resolve it with {@code valueOf}. A renamed,
     * absent or unrecognised value cannot be attributed to a rebate account.
     */
    @Test
    void refusesAFundingNetworkItCannotResolve() {
        assertThrows(IllegalArgumentException.class, () -> listener.onRedemptionCompleted(
                event("rdm_1", "NW-VISA-10", "SWITCH", "24.90", "REDEEMED")));
    }

    /**
     * Strict deserialization, mirroring coupon-service's own strictness towards us. A field we
     * do not know about means their published contract moved without us.
     */
    @Test
    void refusesAPayloadCarryingAFieldWeDoNotKnowAbout() throws Exception {
        String withUnknownField = """
                {
                  "eventType": "northwind.coupon.redemption.completed",
                  "redemptionId": "rdm_1",
                  "couponCode": "NW-VISA-10",
                  "chargeId": "chg_1",
                  "fundingNetwork": "VISA",
                  "discount": "24.90",
                  "status": "REDEEMED",
                  "occurredAt": "2026-09-11T10:14:22Z",
                  "settlementCurrency": "EUR"
                }
                """;

        assertThrows(Exception.class, () -> new ObjectMapper()
                .readValue(withUnknownField, RedemptionCompletedEvent.class));
    }

    @Test
    void readsTheDocumentedPayload() throws Exception {
        String payload = """
                {
                  "eventType": "northwind.coupon.redemption.completed",
                  "redemptionId": "rdm_4f8a21c7",
                  "couponCode": "NW-VISA-10",
                  "chargeId": "chg_9f3b7c21",
                  "fundingNetwork": "VISA",
                  "discount": "24.90",
                  "status": "REDEEMED",
                  "occurredAt": "2026-09-11T10:14:22Z"
                }
                """;

        RedemptionCompletedEvent event = new ObjectMapper()
                .readValue(payload, RedemptionCompletedEvent.class);

        assertEquals("rdm_4f8a21c7", event.redemptionId());
        assertEquals("NW-VISA-10", event.couponCode());
        assertEquals("VISA", event.fundingNetwork());
        assertEquals(new BigDecimal("24.90"), event.discount());
    }

    private static RedemptionCompletedEvent event(String redemptionId, String couponCode,
                                                  String network, String discount, String status) {
        return new RedemptionCompletedEvent(
                "northwind.coupon.redemption.completed",
                redemptionId,
                couponCode,
                "chg_9f3b7c21",
                network,
                new BigDecimal(discount),
                status,
                "2026-09-11T10:14:22Z");
    }
}
