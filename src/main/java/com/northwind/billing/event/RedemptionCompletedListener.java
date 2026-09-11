package com.northwind.billing.event;

import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.reconciliation.InterchangeRates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes {@code northwind.coupon.redemption.completed} from coupon-service.
 *
 * <p>Two jobs, both financial:
 *
 * <ol>
 *   <li><strong>Interchange rebate attribution.</strong> A network-funded promotion is paid for
 *       by that network's interchange rebate. We book the expected rebate against the funding
 *       network so the settlement file and the acquirer's own report can be reconciled.</li>
 *   <li><strong>Refund routing.</strong> A refund on a discounted charge has to be attributed
 *       back to the promotion that funded it, by {@code couponCode}, or the promotion is
 *       refunded out of merchant margin instead of the rebate.</li>
 * </ol>
 *
 * <p><strong>The topic is at-least-once, so this consumer is idempotent on
 * {@code redemptionId}.</strong> That identifier is the deduplication key and it is the only
 * thing standing between a redelivery and a double-booked rebate. An event arriving without one
 * cannot be deduplicated and is rejected rather than processed — see
 * {@link #onRedemptionCompleted}.
 */
@Component
public class RedemptionCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(RedemptionCompletedListener.class);

    /** coupon-service's terminal success status. Only a completed redemption books a rebate. */
    static final String REDEEMED = "REDEEMED";

    private final InterchangeRates interchangeRates;

    /** Deduplication set, keyed on {@code redemptionId}. */
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /** Expected interchange rebate per funding network. */
    private final Map<CardNetwork, BigDecimal> rebate = new ConcurrentHashMap<>();

    public RedemptionCompletedListener(InterchangeRates interchangeRates) {
        this.interchangeRates = interchangeRates;
    }

    public void onRedemptionCompleted(RedemptionCompletedEvent event) {
        if (event.redemptionId() == null || event.redemptionId().isBlank()) {
            // No deduplication key means we cannot tell a redelivery from a new redemption, and
            // a double-booked rebate is a misstated receivable. Refuse rather than guess.
            throw new UndeduplicableEventException(
                    "redemption event for chargeId " + event.chargeId()
                            + " carries no redemptionId — it cannot be deduplicated on an"
                            + " at-least-once topic and will not be booked");
        }

        if (!seen.add(event.redemptionId())) {
            log.debug("ignoring redelivered redemption redemptionId={}", event.redemptionId());
            return;
        }

        if (!REDEEMED.equals(event.status())) {
            log.info("not booking a rebate for a non-terminal redemption redemptionId={} status={}",
                    event.redemptionId(), event.status());
            return;
        }

        // fundingNetwork is a closed set. A value we cannot resolve means we cannot name the
        // account the rebate belongs to, and a rebate booked to the wrong network is worse than
        // one not booked at all.
        CardNetwork network = CardNetwork.valueOf(event.fundingNetwork());

        if (!interchangeRates.isRated(network.code())) {
            throw new UnattributableRebateException(
                    "no agreed interchange rate for " + network.code()
                            + " — the rebate for redemption " + event.redemptionId()
                            + " cannot be attributed");
        }

        BigDecimal expected = event.discount()
                .multiply(interchangeRates.rateFor(network.code()))
                .setScale(2, RoundingMode.HALF_UP);

        rebate.merge(network, expected, BigDecimal::add);

        log.info("booked expected interchange rebate redemptionId={} couponCode={} network={} "
                        + "discount={} rebate={}",
                event.redemptionId(), event.couponCode(), network, event.discount(), expected);
    }

    public BigDecimal expectedRebateFor(CardNetwork network) {
        return rebate.getOrDefault(network, BigDecimal.ZERO.setScale(2));
    }

    /** An event on an at-least-once topic that carries no deduplication key. */
    public static class UndeduplicableEventException extends RuntimeException {
        public UndeduplicableEventException(String message) {
            super(message);
        }
    }

    /** The funding network on the event has no agreed interchange rate. */
    public static class UnattributableRebateException extends RuntimeException {
        public UnattributableRebateException(String message) {
            super(message);
        }
    }
}
