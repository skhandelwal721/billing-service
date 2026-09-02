package com.northwind.billing.charge;

import com.northwind.billing.risk.ChargeRiskGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Charges an invoice. In production since 2024 — every call here moves money.
 *
 * <p>The pre-charge risk check is applied <em>here</em>, not in {@link ChargeService}, because
 * the service is also driven by replay and backfill tooling where the check has already been
 * made upstream. That means the guard is a property of the entrypoint: a new way to charge has
 * to call {@link ChargeRiskGuard#check} for itself. See {@code docs/runbooks/risk.md}.
 */
@RestController
@RequestMapping("/v1/invoices")
public class ChargeController {

    private final ChargeService chargeService;
    private final ChargeRiskGuard chargeRiskGuard;
    private final InvoiceRepository invoiceRepository;

    public ChargeController(ChargeService chargeService,
                            ChargeRiskGuard chargeRiskGuard,
                            InvoiceRepository invoiceRepository) {
        this.chargeService = chargeService;
        this.chargeRiskGuard = chargeRiskGuard;
        this.invoiceRepository = invoiceRepository;
    }

    @PostMapping("/{invoiceId}/charge")
    public ChargeResponse charge(@PathVariable("invoiceId") String invoiceId,
                                 @Valid @RequestBody ChargeRequest request) {
        Invoice invoice = invoiceRepository.require(invoiceId);
        chargeRiskGuard.check(invoice, request);
        return chargeService.charge(invoiceId, request);
    }

    /** Refused before the acquirer was called — no money moved. */
    @ExceptionHandler(ChargeRiskGuard.ChargeDeclinedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String declined(ChargeRiskGuard.ChargeDeclinedException e) {
        return e.getMessage();
    }
}
