package com.northwind.billing.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The daily settlement file.
 *
 * @param settlementDate date the charges were taken
 * @param sections       rows grouped by {@code cardType} — one section per card network,
 *                       booked against that network's settlement account
 * @param exceptions     rows that could not be reconciled, worked by hand the next morning
 */
public record SettlementFile(
        String settlementDate,
        Map<String, List<SettlementRow>> sections,
        List<String> exceptions
) {

    public BigDecimal grossFor(String cardType) {
        return sections.getOrDefault(cardType, List.of()).stream()
                .map(SettlementRow::gross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int rowCount() {
        return sections.values().stream().mapToInt(List::size).sum();
    }
}
