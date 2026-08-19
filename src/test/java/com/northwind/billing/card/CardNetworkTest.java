package com.northwind.billing.card;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardNetworkTest {

    @Test
    void detectsVisaFromBin() {
        assertEquals(CardNetwork.VISA, CardNetwork.fromBin("4111111111111111"));
    }

    @Test
    void detectsMastercardFromBin() {
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.fromBin("5500005555555559"));
    }

    @Test
    void detectsAmexFromBin() {
        assertEquals(CardNetwork.AMEX, CardNetwork.fromBin("378282246310005"));
    }

    @Test
    void rejectsUnsupportedNetwork() {
        assertThrows(IllegalArgumentException.class, () -> CardNetwork.fromBin("6011111111111117"));
    }

    /**
     * These strings appear in the charge response and in the finance reconciliation export.
     * Changing one is a breaking change for both consumers, so pin them.
     */
    @Test
    void networkCodesAreStableContractValues() {
        assertEquals("VISA", CardNetwork.VISA.code());
        assertEquals("MASTERCARD", CardNetwork.MASTERCARD.code());
        assertEquals("AMEX", CardNetwork.AMEX.code());
    }
}
