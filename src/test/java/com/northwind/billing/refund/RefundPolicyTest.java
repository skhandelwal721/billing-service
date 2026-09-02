package com.northwind.billing.refund;

import com.northwind.billing.card.CardNetwork;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundPolicyTest {

    private final RefundPolicy policy = new RefundPolicy();

    @Test
    void visaWindowIsOneHundredAndEightyDays() {
        assertEquals(Duration.ofDays(180), policy.windowFor(CardNetwork.VISA));
    }

    @Test
    void mastercardWindowIsOneHundredAndTwentyDays() {
        assertEquals(Duration.ofDays(120), policy.windowFor(CardNetwork.MASTERCARD));
    }

    /**
     * Guard rail for anyone adding a card network.
     *
     * <p>A network we can charge but cannot refund leaves money stranded, so this enumerates
     * {@link CardNetwork#values()} rather than listing the networks we happen to support today.
     * If this test fails because a new network was added, the fix is a scheme refund window in
     * {@code RefundPolicy}, not an exclusion here.
     */
    @Test
    void everyNetworkHasARefundWindow() {
        for (CardNetwork network : CardNetwork.values()) {
            assertDoesNotThrow(() -> policy.windowFor(network),
                    "no refund window recorded for " + network);
        }
    }

    @Test
    void chargeInsideWindowIsRefundable() {
        assertTrue(policy.isWithinWindow(CardNetwork.VISA, Duration.ofDays(30)));
    }

    @Test
    void chargeOutsideWindowIsNotRefundable() {
        assertFalse(policy.isWithinWindow(CardNetwork.MASTERCARD, Duration.ofDays(150)));
    }
}
