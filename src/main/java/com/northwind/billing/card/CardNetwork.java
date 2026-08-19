package com.northwind.billing.card;

/**
 * Card networks billing-service can charge.
 *
 * <p>{@link #code()} is what appears in the {@code cardType} field of the charge response and
 * in the reconciliation export. These strings are part of a published contract — finance
 * groups the daily settlement file by them.
 */
public enum CardNetwork {

    VISA("VISA"),
    MASTERCARD("MASTERCARD"),
    AMEX("AMEX");

    private final String code;

    CardNetwork(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CardNetwork fromBin(String pan) {
        if (pan == null || pan.isBlank()) {
            throw new IllegalArgumentException("card number is required");
        }
        char first = pan.charAt(0);
        return switch (first) {
            case '4' -> VISA;
            case '5' -> MASTERCARD;
            case '3' -> AMEX;
            default -> throw new IllegalArgumentException("unsupported card network for this card");
        };
    }
}
