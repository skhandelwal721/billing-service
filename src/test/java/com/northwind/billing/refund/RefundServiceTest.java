package com.northwind.billing.refund;

import com.northwind.billing.acquirer.AcquirerClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundServiceTest {

    private final RefundService refundService =
            new RefundService(new AcquirerClient("test-key"), new RefundPolicy());

    @Test
    void refundsAWorldpayReference() {
        RefundReceipt receipt = refundService.refund(new RefundRequest(
                "wp_4f8a21c7", "VISA", new BigDecimal("298.80"), 10));

        assertEquals("wp_4f8a21c7", receipt.acquirerReference());
        assertEquals("REFUNDED", receipt.status());
        assertEquals(new BigDecimal("298.80"), receipt.amount());
    }

    /**
     * Refund routing is prefix-based on the acquirer reference. Any reference format we do not
     * recognise is money we cannot give back from here.
     */
    @Test
    void rejectsAReferenceFromAnotherAcquirer() {
        RefundService.UnroutableRefundException e = assertThrows(
                RefundService.UnroutableRefundException.class,
                () -> refundService.refund(new RefundRequest(
                        "st_9c1d44", "VISA", new BigDecimal("10.00"), 1)));

        assertTrue(e.getMessage().contains("st_9c1d44"));
    }

    @Test
    void rejectsARefundOutsideTheSchemeWindow() {
        assertThrows(RefundService.RefundWindowExpiredException.class,
                () -> refundService.refund(new RefundRequest(
                        "wp_4f8a21c7", "MASTERCARD", new BigDecimal("10.00"), 200)));
    }
}
