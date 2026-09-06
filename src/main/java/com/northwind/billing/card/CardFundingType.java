package com.northwind.billing.card;

/**
 * How a card is funded.
 *
 * <p>Introduced with Amex support. Amex is a charge card rather than a revolving credit
 * product, which matters for how we present the charge and for the interchange we pay, so the
 * charge response now reports funding type alongside the network.
 */
public enum CardFundingType {

    CREDIT("CREDIT"),
    CHARGE_CARD("CHARGE_CARD");

    private final String code;

    CardFundingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CardFundingType forNetwork(CardNetwork network) {
        return network == CardNetwork.AMEX ? CHARGE_CARD : CREDIT;
    }
}
