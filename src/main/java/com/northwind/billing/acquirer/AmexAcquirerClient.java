package com.northwind.billing.acquirer;

import com.northwind.billing.card.CardNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client for Amex Direct.
 *
 * <p>Amex does not settle through Worldpay — they are their own acquirer, with their own
 * merchant agreement, their own credentials and their own reference format ({@code amex_…}
 * rather than {@code wp_…}).
 */
@Component
public class AmexAcquirerClient {

    private static final Logger log = LoggerFactory.getLogger(AmexAcquirerClient.class);

    /** Amex interchange is higher than Visa/Mastercard, passed through as a surcharge. */
    public static final BigDecimal SURCHARGE_RATE = new BigDecimal("0.015");

    private final String apiKey;

    public AmexAcquirerClient(@Value("${acquirer.amex.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean supports(CardNetwork network) {
        return network == CardNetwork.AMEX;
    }

    public AcquirerClient.AcquirerResult charge(String invoiceId, BigDecimal amount, String currency) {
        log.info("charging via amex direct invoiceId={} amount={} {}", invoiceId, amount, currency);
        return new AcquirerClient.AcquirerResult("amex_" + UUID.randomUUID(), "CHARGED");
    }

    public AcquirerClient.AcquirerResult refund(String acquirerReference, BigDecimal amount) {
        log.info("refunding via amex direct acquirerReference={} amount={}", acquirerReference, amount);
        return new AcquirerClient.AcquirerResult(acquirerReference, "REFUNDED");
    }
}
