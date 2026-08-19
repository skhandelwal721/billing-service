package com.northwind.billing.charge;

import com.northwind.billing.acquirer.AcquirerClient;
import com.northwind.billing.acquirer.AmexAcquirerClient;
import com.northwind.billing.card.CardFundingType;
import com.northwind.billing.card.CardNetwork;
import com.northwind.billing.client.TaxServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private final AcquirerClient acquirerClient;
    private final AmexAcquirerClient amexAcquirerClient;
    private final TaxServiceClient taxServiceClient;
    private final InvoiceRepository invoiceRepository;

    public ChargeService(AcquirerClient acquirerClient,
                         AmexAcquirerClient amexAcquirerClient,
                         TaxServiceClient taxServiceClient,
                         InvoiceRepository invoiceRepository) {
        this.acquirerClient = acquirerClient;
        this.amexAcquirerClient = amexAcquirerClient;
        this.taxServiceClient = taxServiceClient;
        this.invoiceRepository = invoiceRepository;
    }

    public ChargeResponse charge(String invoiceId, ChargeRequest request) {
        Invoice invoice = invoiceRepository.require(invoiceId);
        CardNetwork network = CardNetwork.fromBin(request.cardNumber());

        BigDecimal subtotal = invoice.subtotal().setScale(2, RoundingMode.HALF_UP);

        BigDecimal surcharge = network == CardNetwork.AMEX
                ? subtotal.multiply(AmexAcquirerClient.SURCHARGE_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        BigDecimal taxable = subtotal.add(surcharge);
        BigDecimal tax = taxServiceClient
                .taxFor(invoiceId, taxable, request.currency(), request.billingPostcode())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax);

        AcquirerClient.AcquirerResult result = network == CardNetwork.AMEX
                ? amexAcquirerClient.charge(invoiceId, total, request.currency())
                : acquirerClient.charge(invoiceId, total, request.currency(), network);

        String chargeId = "chg_" + UUID.randomUUID();
        log.info("charged invoiceId={} chargeId={} network={} total={} {}",
                invoiceId, chargeId, network, total, request.currency());

        return new ChargeResponse(
                chargeId,
                invoiceId,
                subtotal,
                surcharge,
                tax,
                total,
                request.currency(),
                CardFundingType.forNetwork(network).code(),
                network.code(),
                result.acquirerReference(),
                result.status());
    }
}
