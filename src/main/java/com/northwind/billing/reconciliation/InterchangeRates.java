package com.northwind.billing.reconciliation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Interchange rate we pay per card network, used to work out the net settlement amount finance
 * expects to see land in the bank.
 *
 * <p>Keyed by the <strong>{@code cardType} string from the charge response</strong>, not by the
 * {@link com.northwind.billing.card.CardNetwork} enum, because the settlement file is built
 * from stored charge responses — the enum is long gone by the time reconciliation runs. See
 * {@code docs/api/charge.md}: {@code cardType} is the network, {@code "VISA"} or
 * {@code "MASTERCARD"}.
 */
@Component
public class InterchangeRates {

    /**
     * Rates as agreed with each network.
     *
     * <p>The keys here are contract values, not internal identifiers. They must stay in step
     * with {@code CardNetwork#code()}.
     */
    private static final Map<String, BigDecimal> RATES = Map.of(
            "VISA", new BigDecimal("0.0030"),
            "MASTERCARD", new BigDecimal("0.0035"),
            "AMEX", new BigDecimal("0.0175"));

    /**
     * Rate for a card type, or {@code null} when we have no agreed rate for it.
     *
     * <p>A null here is not benign: {@link SettlementFileBuilder} treats an unrated card type
     * as unreconcilable and drops the row into the exceptions list rather than guessing a fee
     * and reporting a net amount that will not match the bank.
     */
    public BigDecimal rateFor(String cardType) {
        return RATES.get(cardType);
    }

    public boolean isRated(String cardType) {
        return RATES.containsKey(cardType);
    }
}
