package com.neelastack.service;

import com.neelastack.entity.PaymentSource;
import com.neelastack.entity.PaymentWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * What actually happens for a given webhook event type, factored out so the live
 * PaymentWebhookController and the admin replay endpoint (AdminPaymentWebhookController) run
 * exactly the same logic — a replay should behave identically to the original delivery would
 * have, not a hand-rolled approximation of it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookProcessor {

    private final InvoiceService invoiceService;

    /** Runs the side effects for one claimed event. Lets exceptions propagate — the caller decides how to record failure. */
    public void process(PaymentWebhookEvent event) {
        if ("payment.captured".equals(event.getEventType()) && event.getRazorpayOrderId() != null) {
            invoiceService.markPaidFromWebhook(event.getRazorpayOrderId(), event.getRazorpayPaymentId(),
                    PaymentSource.WEBHOOK);
        } else {
            log.info("No handler for webhook event type '{}' (event {}) — recording it as ignored.",
                    event.getEventType(), event.getRazorpayEventId());
        }
    }
}
