package com.northwind.billing.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Client for {@code tax-service}, which owns tax rules and rates.
 *
 * <p>Hard dependency — we do not charge an amount we have not taxed correctly, so there is no
 * fallback. Timeouts are set to tax-service's published p99 of 400ms.
 *
 * <p>The {@code jurisdiction} argument is the VAT place of supply, resolved by
 * {@link PlaceOfSupply} from the charge's {@code billingPostcode}. tax-service applies the rate
 * for that member state — it does not second-guess the jurisdiction we send it, so an
 * incorrectly resolved jurisdiction returns a well-formed tax figure at the wrong rate.
 */
@Component
public class TaxServiceClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofMillis(200);
    static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    private final RestClient restClient;

    public TaxServiceClient(@Value("${clients.tax.baseUrl}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public BigDecimal taxFor(String invoiceId, BigDecimal subtotal, String currency,
                             String postcode, String jurisdiction) {
        return restClient.get()
                .uri("/v1/tax?invoiceId={invoiceId}&amount={amount}&currency={currency}"
                                + "&postcode={postcode}&jurisdiction={jurisdiction}",
                        invoiceId, subtotal, currency, postcode, jurisdiction)
                .retrieve()
                .body(TaxResult.class)
                .taxAmount();
    }

    record TaxResult(BigDecimal taxAmount, String rateId) {
    }
}
