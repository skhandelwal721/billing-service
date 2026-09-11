package com.northwind.billing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the VAT place of supply for a charge.
 *
 * <p>Under EU Council Directive 2006/112/EC the rate applied to a B2C supply is the rate of the
 * customer's member state, not ours. The only signal we have for that on a charge is
 * {@code billingPostcode}, which the caller supplies and we forward to {@code tax-service} as
 * the jurisdiction input.
 *
 * <p><strong>The postcode is optional on the wire and load-bearing in law.</strong> That
 * combination is the hazard. {@code ChargeRequest.billingPostcode} has no
 * {@code @NotBlank} — it was made optional so the internal reconciliation replay path could
 * re-charge historic invoices that predate its collection — so a caller that stops sending it
 * gets a successful charge at the wrong rate rather than a validation error.
 *
 * <p>When we cannot resolve a jurisdiction we fall back to {@link #HOME_JURISDICTION}, the
 * merchant's own establishment. That fallback is correct for a domestic supply and <em>wrong for
 * every cross-border one</em>: a German consumer charged at the GB rate is under-collected by
 * one point, and the declaration goes to the wrong member state. It is a misdeclaration, not an
 * outage, so nothing here throws and no alarm fires.
 *
 * <p>See {@code docs/api/charge.md}. Callers: {@code coupon-service} via
 * {@code POST /v1/charges}, and the finance reconciliation replay job.
 */
@Component
public class PlaceOfSupply {

    private static final Logger log = LoggerFactory.getLogger(PlaceOfSupply.class);

    /** The merchant's own establishment. Used only when no jurisdiction can be resolved. */
    public static final String HOME_JURISDICTION = "GB";

    /**
     * Postcode prefix to member state, for the storefronts we operate. Ordered longest-first so
     * a more specific prefix wins.
     */
    private static final Map<String, String> PREFIXES = new LinkedHashMap<>();

    static {
        PREFIXES.put("DE-", "DE");
        PREFIXES.put("FR-", "FR");
        PREFIXES.put("NL-", "NL");
        PREFIXES.put("ES-", "ES");
        PREFIXES.put("IE-", "IE");
        PREFIXES.put("GB-", "GB");
    }

    /**
     * The member state whose VAT rate applies.
     *
     * @param billingPostcode as supplied on the charge request, may be {@code null}
     * @return the resolved jurisdiction, or {@link #HOME_JURISDICTION} if it cannot be resolved
     */
    public String forPostcode(String billingPostcode) {
        if (billingPostcode == null || billingPostcode.isBlank()) {
            log.warn("no billingPostcode on the charge — falling back to {} for place of supply."
                    + " A cross-border supply taxed at the home rate is a misdeclaration in the"
                    + " customer's member state", HOME_JURISDICTION);
            return HOME_JURISDICTION;
        }

        String normalised = billingPostcode.trim().toUpperCase();

        for (Map.Entry<String, String> prefix : PREFIXES.entrySet()) {
            if (normalised.startsWith(prefix.getKey())) {
                return prefix.getValue();
            }
        }

        log.warn("unrecognised billingPostcode prefix — falling back to {} for place of supply",
                HOME_JURISDICTION);
        return HOME_JURISDICTION;
    }

    /** True when the jurisdiction was resolved from the request rather than defaulted. */
    public boolean isResolved(String billingPostcode) {
        return billingPostcode != null
                && !billingPostcode.isBlank()
                && PREFIXES.keySet().stream()
                        .anyMatch(p -> billingPostcode.trim().toUpperCase().startsWith(p));
    }
}
