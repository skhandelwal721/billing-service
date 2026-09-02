package com.northwind.billing.refund;

import com.northwind.billing.acquirer.AcquirerClient;
import com.northwind.billing.card.CardNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Refunds a charge this service already took.
 *
 * <p>A refund is only ever possible against the {@code acquirerReference} we recorded at charge
 * time, and <strong>only the acquirer that issued a reference can refund it</strong>. We
 * recognise Worldpay's reference format and route to {@link AcquirerClient#refund}; a reference
 * in any other format cannot be routed from here and the money stays taken until someone
 * refunds it by hand out of the acquirer's own portal.
 *
 * <p>This is the code path the {@code docs/runbooks/charge.md} rollback section depends on: the
 * previous release must be able to refund every reference the new release could have created.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /**
     * Prefix Worldpay puts on every reference it issues — see
     * {@code AcquirerClient#charge}, which builds {@code "wp_" + UUID}.
     *
     * <p>Reference routing is prefix-based because the acquirer is not carried on the refund
     * request; the reference is all finance has when it reconciles the settlement report.
     */
    static final String WORLDPAY_REFERENCE_PREFIX = "wp_";

    private final AcquirerClient acquirerClient;
    private final RefundPolicy refundPolicy;

    public RefundService(AcquirerClient acquirerClient, RefundPolicy refundPolicy) {
        this.acquirerClient = acquirerClient;
        this.refundPolicy = refundPolicy;
    }

    public RefundReceipt refund(RefundRequest request) {
        String acquirerReference = request.acquirerReference();

        if (!acquirerReference.startsWith(WORLDPAY_REFERENCE_PREFIX)) {
            throw new UnroutableRefundException(acquirerReference);
        }

        CardNetwork network = CardNetwork.valueOf(request.cardType());
        Duration sinceCharge = Duration.ofDays(request.daysSinceCharge());
        if (!refundPolicy.isWithinWindow(network, sinceCharge)) {
            throw new RefundWindowExpiredException(acquirerReference, network,
                    refundPolicy.windowFor(network));
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        AcquirerClient.AcquirerResult result = acquirerClient.refund(acquirerReference, amount);

        log.info("refunded acquirerReference={} network={} amount={} status={}",
                acquirerReference, network, amount, result.status());

        return new RefundReceipt(
                result.acquirerReference(),
                request.cardType(),
                amount,
                result.status());
    }

    /**
     * The reference was issued by something other than Worldpay, so we have no client that can
     * refund it. Money has already moved and this service cannot move it back.
     */
    public static class UnroutableRefundException extends RuntimeException {
        public UnroutableRefundException(String acquirerReference) {
            super("cannot route a refund for acquirerReference=" + acquirerReference
                    + " — only Worldpay references (" + WORLDPAY_REFERENCE_PREFIX
                    + "…) can be refunded by this service");
        }
    }

    /** Past the scheme refund window for the network. */
    public static class RefundWindowExpiredException extends RuntimeException {
        public RefundWindowExpiredException(String acquirerReference, CardNetwork network, Duration window) {
            super("acquirerReference=" + acquirerReference + " is outside the " + window.toDays()
                    + "-day " + network + " refund window");
        }
    }
}
