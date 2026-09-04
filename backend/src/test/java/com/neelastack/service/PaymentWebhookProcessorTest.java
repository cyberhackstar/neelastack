package com.neelastack.service;

import com.neelastack.entity.PaymentSource;
import com.neelastack.entity.PaymentWebhookEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PaymentWebhookProcessor is intentionally thin — its only job is to be the single place
 * that decides what a given event type does, so the live webhook endpoint and admin replay
 * can't drift into handling the same event type differently.
 */
class PaymentWebhookProcessorTest {

    @Test
    void paymentCaptured_withOrderId_marksInvoicePaid() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        PaymentWebhookProcessor processor = new PaymentWebhookProcessor(invoiceService);

        PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                .eventType("payment.captured")
                .razorpayOrderId("order_1")
                .razorpayPaymentId("pay_1")
                .rawPayload("{}")
                .build();

        processor.process(event);

        verify(invoiceService).markPaidFromWebhook(eq("order_1"), eq("pay_1"), eq(PaymentSource.WEBHOOK));
    }

    @Test
    void unhandledEventType_doesNotTouchInvoices() {
        InvoiceService invoiceService = mock(InvoiceService.class);
        PaymentWebhookProcessor processor = new PaymentWebhookProcessor(invoiceService);

        PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                .eventType("order.paid")
                .razorpayOrderId("order_1")
                .rawPayload("{}")
                .build();

        processor.process(event);

        verifyNoInteractions(invoiceService);
    }

    @Test
    void paymentCaptured_withoutOrderId_doesNotTouchInvoices() {
        // Defensive case — shouldn't happen for a real payment.captured payload, but must not
        // NPE or silently misattribute a payment if it ever does.
        InvoiceService invoiceService = mock(InvoiceService.class);
        PaymentWebhookProcessor processor = new PaymentWebhookProcessor(invoiceService);

        PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                .eventType("payment.captured")
                .razorpayOrderId(null)
                .rawPayload("{}")
                .build();

        processor.process(event);

        verifyNoInteractions(invoiceService);
    }
}
