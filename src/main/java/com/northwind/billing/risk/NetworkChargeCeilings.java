package com.northwind.billing.risk;

import com.northwind.billing.card.CardNetwork;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Largest single charge we will take on each card network without a manual review.
 *
 * <p>The ceilings are not ours to pick freely — they come out of the chargeback exposure we
 * carry on each network, which differs by scheme. A network with no ceiling recorded is a
 * network we have not agreed an exposure limit for, and treating that as "no limit" is how a
 * fraud run gets settled before anyone notices.
 */
@Component
public class NetworkChargeCeilings {

    /**
     * Per-network ceilings, in the charge currency.
     *
     * <p><strong>Add an entry whenever {@link CardNetwork} gains a value.</strong>
     * {@code ChargeRiskGuardTest#everyNetworkHasACeiling} enumerates
     * {@code CardNetwork.values()} against this map so a new network cannot ship unlimited.
     */
    private static final Map<CardNetwork, BigDecimal> CEILINGS = Map.of(
            CardNetwork.VISA, new BigDecimal("5000.00"),
            CardNetwork.MASTERCARD, new BigDecimal("5000.00"));

    /** Ceiling for a network, or {@code null} when no exposure limit has been agreed. */
    public BigDecimal ceilingFor(CardNetwork network) {
        return CEILINGS.get(network);
    }

    public boolean hasCeiling(CardNetwork network) {
        return CEILINGS.containsKey(network);
    }
}
