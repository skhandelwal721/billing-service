package com.northwind.billing.refund;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refunds a charge. Used by support tooling and by the finance reconciliation flow when a
 * settlement line has to be reversed.
 */
@RestController
@RequestMapping("/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public RefundReceipt refund(@Valid @RequestBody RefundRequest request) {
        return refundService.refund(request);
    }

    /**
     * A reference we cannot route is not the caller's fault and retrying will not help — it
     * means the charge was taken through an acquirer this release cannot refund.
     */
    @ExceptionHandler(RefundService.UnroutableRefundException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public String unroutable(RefundService.UnroutableRefundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(RefundService.RefundWindowExpiredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String windowExpired(RefundService.RefundWindowExpiredException e) {
        return e.getMessage();
    }
}
