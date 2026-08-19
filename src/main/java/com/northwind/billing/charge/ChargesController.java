package com.northwind.billing.charge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The new consolidated billing API.
 *
 * <p>Takes the invoice in the body rather than the path, so a caller no longer has to look the
 * invoice up before charging it — one round trip instead of two. Supports every network we
 * accept, including Amex.
 *
 * <p>{@code POST /v1/invoices/{invoiceId}/charge} stays in place and behaves identically.
 */
@RestController
@RequestMapping("/v1/charges")
public class ChargesController {

    private final ChargeService chargeService;

    public ChargesController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResponse create(@Valid @RequestBody ChargeCommand command) {
        return chargeService.charge(
                command.invoiceId(),
                new ChargeRequest(command.cardNumber(), command.currency(), command.billingPostcode()));
    }

    public record ChargeCommand(

            @NotBlank(message = "invoiceId is required")
            String invoiceId,

            @NotBlank(message = "cardNumber is required")
            String cardNumber,

            @NotBlank(message = "currency is required")
            String currency,

            String billingPostcode
    ) {
    }
}
