package com.beaconstone.billing.charge;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Repository
public class InvoiceRepository {

    private final Map<String, Invoice> invoices = new ConcurrentHashMap<>();

    public InvoiceRepository() {
        invoices.put("inv-1001", new Invoice("inv-1001", "cust-77", new BigDecimal("249.00"), "OPEN"));
        invoices.put("inv-1002", new Invoice("inv-1002", "cust-91", new BigDecimal("18.50"), "OPEN"));
    }

    public Optional<Invoice> find(String invoiceId) {
        return Optional.ofNullable(invoices.get(invoiceId));
    }

    public Invoice require(String invoiceId) {
        return find(invoiceId)
                .orElseThrow(() -> new NoSuchElementException("invoice not found: " + invoiceId));
    }
}
