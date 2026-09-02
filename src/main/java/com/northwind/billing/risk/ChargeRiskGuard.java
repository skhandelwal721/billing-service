package com.northwind.billing.risk;

import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.charge.ChargeRequest;
import com.northwind.billing.charge.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pre-charge risk check. Runs <strong>before</strong> anything is sent to an acquirer, because
 * a charge is irreversible once taken — a declined charge is a support ticket, a fraudulent
 * charge that settles is a chargeback and a refund we may not be able to route.
 *
 * <p>Two checks, both cheap and both network-aware:
 *
 * <ol>
 *   <li>the invoice amount is within the agreed exposure ceiling for the card network, and</li>
 *   <li>the network has an exposure ceiling at all — a network with no agreed limit is
 *       declined rather than waved through.</li>
 * </ol>
 *
 * <p><strong>Every path that takes a charge must call {@link #check} first.</strong> There is
 * no interceptor or filter doing this globally; it is an explicit call from the charge
 * entrypoint, so a new entrypoint that forgets it silently ships unguarded charging. See
 * {@code docs/runbooks/risk.md}.
 */
@Component
public class ChargeRiskGuard {

    private static final Logger log = LoggerFactory.getLogger(ChargeRiskGuard.class);

    private final NetworkChargeCeilings ceilings;

    public ChargeRiskGuard(NetworkChargeCeilings ceilings) {
        this.ceilings = ceilings;
    }

    public void check(Invoice invoice, ChargeRequest request) {
        CardNetwork network = CardNetwork.fromBin(request.cardNumber());
        BigDecimal amount = invoice.subtotal();

        if (!ceilings.hasCeiling(network)) {
            log.warn("declining invoiceId={} network={} — no agreed exposure ceiling",
                    invoice.invoiceId(), network);
            throw new ChargeDeclinedException(
                    "no agreed exposure ceiling for network " + network
                            + " — charges on this network need a limit before they can be taken");
        }

        BigDecimal ceiling = ceilings.ceilingFor(network);
        if (amount.compareTo(ceiling) > 0) {
            log.warn("declining invoiceId={} network={} amount={} over ceiling={}",
                    invoice.invoiceId(), network, amount, ceiling);
            throw new ChargeDeclinedException(
                    "amount " + amount + " is over the " + ceiling + " ceiling for " + network
                            + " — needs manual review");
        }
    }

    /** The charge was refused before it reached an acquirer. No money moved. */
    public static class ChargeDeclinedException extends RuntimeException {
        public ChargeDeclinedException(String message) {
            super(message);
        }
    }
}
