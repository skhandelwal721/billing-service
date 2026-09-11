package com.northwind.billing.charge;

import com.northwind.billing.acquirer.AcquirerClient;
import com.northwind.billing.acquirer.AmexAcquirerClient;
import com.northwind.billing.card.CardFundingType;
import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.client.PlaceOfSupply;
import com.northwind.billing.client.TaxServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final AcquirerClient acquirerClient;
    private final AmexAcquirerClient amexAcquirerClient;
    private final TaxServiceClient taxServiceClient;
    private final InvoiceRepository invoiceRepository;
    private final PlaceOfSupply placeOfSupply;

    public ChargeService(AcquirerClient acquirerClient,
                         AmexAcquirerClient amexAcquirerClient,
                         TaxServiceClient taxServiceClient,
                         InvoiceRepository invoiceRepository,
                         PlaceOfSupply placeOfSupply) {
        this.acquirerClient = acquirerClient;
        this.amexAcquirerClient = amexAcquirerClient;
        this.taxServiceClient = taxServiceClient;
        this.invoiceRepository = invoiceRepository;
        this.placeOfSupply = placeOfSupply;
    }

    public ChargeResponse charge(String invoiceId, ChargeRequest request) {
        Invoice invoice = invoiceRepository.require(invoiceId);
        CardNetwork network = CardNetwork.fromBin(request.cardNumber());

        BigDecimal subtotal = applyPromotion(
                invoiceId,
                invoice.subtotal().setScale(2, RoundingMode.HALF_UP),
                request.promotionalAdjustment());

        BigDecimal surcharge = network == CardNetwork.AMEX
                ? subtotal.multiply(AmexAcquirerClient.SURCHARGE_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        BigDecimal taxable = subtotal.add(surcharge);

        // EU place of supply. Resolved from billingPostcode, which is optional on the wire and
        // load-bearing in law — see PlaceOfSupply. An unresolved jurisdiction taxes a
        // cross-border supply at our home rate and declares it in the wrong member state.
        String jurisdiction = placeOfSupply.forPostcode(request.billingPostcode());
        if (!placeOfSupply.isResolved(request.billingPostcode())) {
            log.warn("charging invoiceId={} with an unresolved VAT jurisdiction — defaulted to {}",
                    invoiceId, jurisdiction);
        }

        BigDecimal tax = taxServiceClient
                .taxFor(invoiceId, taxable, request.currency(), request.billingPostcode(),
                        jurisdiction)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax);

        AcquirerClient.AcquirerResult result = network == CardNetwork.AMEX
                ? amexAcquirerClient.charge(invoiceId, total, request.currency())
                : acquirerClient.charge(invoiceId, total, request.currency(), network);

        String chargeId = "chg_" + UUID.randomUUID();
        log.info("charged invoiceId={} chargeId={} network={} total={} {}",
                invoiceId, chargeId, network, total, request.currency());

        return new ChargeResponse(
                chargeId,
                invoiceId,
                subtotal,
                surcharge,
                tax,
                total,
                request.currency(),
                CardFundingType.forNetwork(network).code(),
                network.code(),
                result.acquirerReference(),
                result.status());
    }

    /**
     * Applies a promotional deduction to the invoice subtotal.
     *
     * <p>The deduction is an <strong>absolute amount in the charge currency</strong>, exactly as
     * {@link ChargeRequest#promotionalAdjustment} documents. We take it at face value: we have
     * no way to tell a well-formed amount from a well-formed rate, because both are positive
     * decimals smaller than the invoice, so there is nothing here to validate against.
     *
     * <p>What we can check is that the deduction does not exceed the invoice. A deduction larger
     * than the subtotal is refused rather than clamped to zero or charged as a negative — a
     * negative charge is a credit to the cardholder, and taking one by accident is worse than
     * failing the request.
     */
    private BigDecimal applyPromotion(String invoiceId, BigDecimal subtotal, BigDecimal adjustment) {
        if (adjustment == null || adjustment.signum() == 0) {
            return subtotal;
        }

        if (adjustment.signum() < 0) {
            throw new PromotionNotApplicableException(
                    "promotionalAdjustment " + adjustment + " is negative — an adjustment is a"
                            + " deduction, so it cannot increase the amount charged");
        }

        if (adjustment.compareTo(subtotal) > 0) {
            log.warn("refusing charge invoiceId={} adjustment={} exceeds subtotal={}",
                    invoiceId, adjustment, subtotal);
            throw new PromotionNotApplicableException(
                    "promotionalAdjustment " + adjustment + " exceeds the invoice subtotal "
                            + subtotal + " — an absolute amount in the charge currency is"
                            + " expected, see docs/api/charge.md");
        }

        BigDecimal net = subtotal.subtract(adjustment).setScale(2, RoundingMode.HALF_UP);
        log.info("applied promotion invoiceId={} subtotal={} adjustment={} net={}",
                invoiceId, subtotal, adjustment, net);
        return net;
    }

    /** The adjustment supplied cannot be applied to this invoice. */
    public static class PromotionNotApplicableException extends RuntimeException {
        public PromotionNotApplicableException(String message) {
            super(message);
        }
    }
}
