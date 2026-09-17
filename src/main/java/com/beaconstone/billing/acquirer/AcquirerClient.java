package com.beaconstone.billing.acquirer;

import com.beaconstone.billing.card.CardNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client for the Worldpay acquirer, which settles Visa and Mastercard.
 *
 * <p><strong>Credentials.</strong> The acquirer key is live and tied to the merchant account.
 * It is injected from the platform secret store as {@code ACQUIRER_API_KEY} and must never
 * appear in source or in a deploy descriptor.
 *
 * <p><strong>Irreversibility.</strong> {@link #charge} moves money. A charge can only be
 * undone by calling {@link #refund} with the acquirer reference we were given — there is no
 * way to reverse it from our side, and a reference this acquirer does not recognise cannot be
 * refunded at all. Any change that introduces a second acquirer has to keep the refund path
 * working for references issued by both, in both directions of a deploy.
 */
@Component
public class AcquirerClient {

    private static final Logger log = LoggerFactory.getLogger(AcquirerClient.class);

    private final String apiKey;

    public AcquirerClient(@Value("${acquirer.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean supports(CardNetwork network) {
        return network == CardNetwork.VISA || network == CardNetwork.MASTERCARD;
    }

    public AcquirerResult charge(String invoiceId, BigDecimal amount, String currency, CardNetwork network) {
        log.info("charging invoiceId={} amount={} {} network={}", invoiceId, amount, currency, network);
        return new AcquirerResult("wp_" + UUID.randomUUID(), "CHARGED");
    }

    public AcquirerResult refund(String acquirerReference, BigDecimal amount) {
        log.info("refunding acquirerReference={} amount={}", acquirerReference, amount);
        return new AcquirerResult(acquirerReference, "REFUNDED");
    }

    public record AcquirerResult(String acquirerReference, String status) {
    }
}
