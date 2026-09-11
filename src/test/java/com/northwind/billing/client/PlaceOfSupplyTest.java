package com.northwind.billing.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceOfSupplyTest {

    private final PlaceOfSupply placeOfSupply = new PlaceOfSupply();

    @Test
    void resolvesEachStorefrontJurisdictionFromThePostcode() {
        assertEquals("DE", placeOfSupply.forPostcode("DE-10115"));
        assertEquals("FR", placeOfSupply.forPostcode("FR-75001"));
        assertEquals("NL", placeOfSupply.forPostcode("NL-1012"));
        assertEquals("ES", placeOfSupply.forPostcode("ES-28001"));
        assertEquals("IE", placeOfSupply.forPostcode("IE-D02"));
        assertEquals("GB", placeOfSupply.forPostcode("GB-EC2A"));
    }

    @Test
    void normalisesCaseAndWhitespace() {
        assertEquals("DE", placeOfSupply.forPostcode("  de-10115 "));
    }

    /**
     * The hazard, documented deliberately.
     *
     * <p>{@code billingPostcode} is optional on {@code ChargeRequest}, so a caller that stops
     * sending it gets a successful charge at the home rate. Under Directive 2006/112/EC a B2C
     * supply is taxed in the customer's member state, so a German consumer charged at the GB
     * rate is a misdeclaration — and nothing here throws.
     */
    @Test
    void fallsBackToTheHomeJurisdictionWhenThePostcodeIsAbsent() {
        assertEquals(PlaceOfSupply.HOME_JURISDICTION, placeOfSupply.forPostcode(null));
        assertEquals(PlaceOfSupply.HOME_JURISDICTION, placeOfSupply.forPostcode(""));
        assertEquals(PlaceOfSupply.HOME_JURISDICTION, placeOfSupply.forPostcode("   "));
    }

    @Test
    void fallsBackToTheHomeJurisdictionForAnUnrecognisedPrefix() {
        assertEquals(PlaceOfSupply.HOME_JURISDICTION, placeOfSupply.forPostcode("SE-11122"));
    }

    /** Distinguishes a resolved jurisdiction from a defaulted one, so a caller can alarm on it. */
    @Test
    void reportsWhetherTheJurisdictionWasResolvedOrDefaulted() {
        assertTrue(placeOfSupply.isResolved("DE-10115"));
        assertFalse(placeOfSupply.isResolved(null));
        assertFalse(placeOfSupply.isResolved(""));
        assertFalse(placeOfSupply.isResolved("SE-11122"));
    }
}
