package com.northwind.billing.reconciliation;

import com.northwind.billing.charge.ChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the daily settlement file finance reconciles against the acquirer's own report.
 *
 * <p>Input is the charge responses we emitted that day — the published contract in
 * {@code docs/api/charge.md}, read back out of the charge log. Two fields of that contract are
 * load-bearing here:
 *
 * <ul>
 *   <li><strong>{@code cardType}</strong> is the grouping key. The file has one section per
 *       card network and finance books each section against that network's settlement account.
 *       A value that is not a network name puts real money under a heading that does not
 *       correspond to anything the bank will pay us.</li>
 *   <li><strong>{@code subtotal}, {@code tax} and {@code total}</strong> must agree:
 *       {@code subtotal + tax == total}. This is the only integrity check standing between a
 *       mispriced charge and the general ledger, and it is checked per row.</li>
 * </ul>
 *
 * <p>Rows that fail either check are not silently corrected — they go into
 * {@link SettlementFile#exceptions()}, which finance works through by hand the next morning.
 */
@Component
public class SettlementFileBuilder {

    private static final Logger log = LoggerFactory.getLogger(SettlementFileBuilder.class);

    private final InterchangeRates interchangeRates;

    public SettlementFileBuilder(InterchangeRates interchangeRates) {
        this.interchangeRates = interchangeRates;
    }

    public SettlementFile build(String settlementDate, List<ChargeResponse> charges) {
        Map<String, List<SettlementRow>> sections = new LinkedHashMap<>();
        List<String> exceptions = new ArrayList<>();

        for (ChargeResponse charge : charges) {
            String cardType = charge.cardType();

            if (!balances(charge)) {
                exceptions.add("chargeId=" + charge.chargeId()
                        + " does not balance: subtotal " + charge.subtotal()
                        + " + tax " + charge.tax() + " != total " + charge.total());
                continue;
            }

            if (!interchangeRates.isRated(cardType)) {
                exceptions.add("chargeId=" + charge.chargeId()
                        + " has no agreed interchange rate for cardType=" + cardType
                        + " — cannot compute a net settlement amount");
                continue;
            }

            BigDecimal interchange = charge.total()
                    .multiply(interchangeRates.rateFor(cardType))
                    .setScale(2, RoundingMode.HALF_UP);

            sections.computeIfAbsent(cardType, key -> new ArrayList<>())
                    .add(new SettlementRow(
                            charge.chargeId(),
                            charge.acquirerReference(),
                            cardType,
                            charge.total(),
                            interchange,
                            charge.total().subtract(interchange),
                            charge.currency(),
                            charge.status()));
        }

        if (!exceptions.isEmpty()) {
            log.warn("settlement file for {} has {} exception row(s)", settlementDate, exceptions.size());
        }

        return new SettlementFile(settlementDate, sections, exceptions);
    }

    /**
     * The charge response carries the arithmetic that produced the amount we took. If it does
     * not add up, either we charged the wrong amount or the contract changed underneath us —
     * both are finance escalations, not rounding to be papered over.
     */
    private boolean balances(ChargeResponse charge) {
        return charge.subtotal().add(charge.tax()).compareTo(charge.total()) == 0;
    }
}
