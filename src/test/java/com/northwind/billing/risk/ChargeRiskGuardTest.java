package com.northwind.billing.risk;

import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.charge.ChargeRequest;
import com.northwind.billing.charge.Invoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeRiskGuardTest {

    private static final String VISA_PAN = "4111111111111111";
    private static final String MASTERCARD_PAN = "5500005555555559";

    private final ChargeRiskGuard guard = new ChargeRiskGuard(new NetworkChargeCeilings());

    @Test
    void allowsAChargeUnderTheCeiling() {
        assertDoesNotThrow(() -> guard.check(invoice("249.00"), request(VISA_PAN)));
    }

    @Test
    void declinesAChargeOverTheCeiling() {
        ChargeRiskGuard.ChargeDeclinedException e = assertThrows(
                ChargeRiskGuard.ChargeDeclinedException.class,
                () -> guard.check(invoice("9000.00"), request(MASTERCARD_PAN)));

        assertTrue(e.getMessage().contains("ceiling"));
    }

    /**
     * A network with no agreed exposure ceiling is declined, not waved through. Treating a
     * missing limit as "unlimited" is how a fraud run settles before anyone notices.
     */
    @Test
    void declinesANetworkWithNoAgreedCeiling() {
        NetworkChargeCeilings noCeilings = new NetworkChargeCeilings() {
            @Override
            public boolean hasCeiling(CardNetwork network) {
                return false;
            }
        };

        assertThrows(ChargeRiskGuard.ChargeDeclinedException.class,
                () -> new ChargeRiskGuard(noCeilings).check(invoice("10.00"), request(VISA_PAN)));
    }

    /**
     * Guard rail for anyone adding a card network: charges we can take but cannot limit are
     * unbounded exposure. Enumerates {@link CardNetwork#values()} on purpose, so a new network
     * without an agreed ceiling fails the build rather than shipping unlimited.
     */
    @Test
    void everyNetworkHasACeiling() {
        NetworkChargeCeilings ceilings = new NetworkChargeCeilings();
        for (CardNetwork network : CardNetwork.values()) {
            assertTrue(ceilings.hasCeiling(network),
                    "no agreed exposure ceiling for " + network
                            + " — charges on this network would be unlimited");
        }
    }

    private static Invoice invoice(String subtotal) {
        return new Invoice("inv-1001", "cust-77", new BigDecimal(subtotal), "OPEN");
    }

    private static ChargeRequest request(String pan) {
        return new ChargeRequest(pan, "GBP", "EC2A 4BX");
    }
}
