package com.beaconstone.billing.charge;

import java.math.BigDecimal;

public record Invoice(String invoiceId, String customerId, BigDecimal subtotal, String status) {
}
