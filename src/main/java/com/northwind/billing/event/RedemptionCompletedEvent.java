package com.northwind.billing.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * The {@code northwind.coupon.redemption.completed} payload, as coupon-service publishes it.
 *
 * <p>Hand-written against coupon-service {@code docs/api/redemption.md} at the version pinned in
 * {@code pom.xml} as {@code coupon.contract.version}. This is the only inbound contract we
 * consume from coupon-service — every other interaction is them calling us.
 *
 * <p><strong>Strict on purpose.</strong> {@code ignoreUnknown = false}. We book interchange
 * rebate against the funding network named here and we route promotion-related refunds by
 * {@code couponCode}, so a payload we only partly understand is one we must not act on. A field
 * we do not recognise means their contract moved and our attribution is no longer safe.
 *
 * <p>What we depend on, all documented as stable by coupon-service:
 *
 * <ol>
 *   <li>{@code redemptionId} is unique per redemption and is our <strong>deduplication
 *       key</strong> — the topic is at-least-once.</li>
 *   <li>{@code couponCode} identifies the promotion, and is how a refund is attributed back to
 *       the promotion that funded it.</li>
 *   <li>{@code fundingNetwork} is a card network name, and is the account we book the
 *       interchange rebate against.</li>
 *   <li>{@code discount} is an <strong>absolute currency amount</strong>, the same basis as
 *       {@code promotionalAdjustment} on our own charge request.</li>
 * </ol>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RedemptionCompletedEvent(
        String eventType,
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        String status,
        String occurredAt
) {
}
