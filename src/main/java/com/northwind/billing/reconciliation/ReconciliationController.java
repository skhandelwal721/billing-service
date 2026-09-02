package com.northwind.billing.reconciliation;

import com.northwind.billing.charge.ChargeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Builds the daily settlement file. Driven by the finance reconciliation job, which posts the
 * day's charge responses back and books the result.
 */
@RestController
@RequestMapping("/v1/settlement")
public class ReconciliationController {

    private final SettlementFileBuilder settlementFileBuilder;

    public ReconciliationController(SettlementFileBuilder settlementFileBuilder) {
        this.settlementFileBuilder = settlementFileBuilder;
    }

    @PostMapping("/file")
    public SettlementFile build(@RequestParam("settlementDate") String settlementDate,
                                @RequestBody List<ChargeResponse> charges) {
        return settlementFileBuilder.build(settlementDate, charges);
    }
}
