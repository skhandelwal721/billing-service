package com.northwind.billing.refund;

import com.northwind.billing.card.CardNetwork;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * How long after a charge we are still allowed to refund it.
 *
 * <p>The window is set by the card network's scheme rules, not by us — refunding outside it is
 * rejected by the acquirer and, worse, sometimes accepted and then charged back. Every network
 * we can charge must have a window here, which is why {@link #windowFor} does not have a
 * default: a network with no scheme rule recorded is a network we are not ready to refund, and
 * silently falling back to "180 days" would be us inventing a rule the scheme never agreed to.
 */
@Component
public class RefundPolicy {

    /**
     * Scheme refund windows, keyed by network.
     *
     * <p><strong>Add an entry here whenever {@link CardNetwork} gains a value.</strong>
     * {@code RefundPolicyTest#everyNetworkHasARefundWindow} enumerates
     * {@code CardNetwork.values()} against this map for exactly that reason.
     */
    private static final Map<CardNetwork, Duration> WINDOWS = Map.of(
            CardNetwork.VISA, Duration.ofDays(180),
            CardNetwork.MASTERCARD, Duration.ofDays(120));

    public Duration windowFor(CardNetwork network) {
        Duration window = WINDOWS.get(network);
        if (window == null) {
            throw new IllegalStateException(
                    "no scheme refund window recorded for network " + network
                            + " — refunds for this network cannot be routed");
        }
        return window;
    }

    public boolean isWithinWindow(CardNetwork network, Duration sinceCharge) {
        return sinceCharge.compareTo(windowFor(network)) <= 0;
    }
}
