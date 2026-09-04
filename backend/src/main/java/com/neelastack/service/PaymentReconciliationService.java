package com.neelastack.service;

import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.PaymentSource;
import com.neelastack.repository.InvoiceRepository;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Production reconciliation daemon (master prompt section 3): every 20 minutes, queries
 * Razorpay directly for any invoice that's still PENDING with a Razorpay order already
 * created, and self-heals it to PAID if Razorpay's own records show a captured payment.
 *
 * This exists specifically for the failure modes the webhook alone can't cover: a webhook
 * delivery dropped by network failure, a backend restart mid-processing before the
 * webhook arrives, or a webhook misconfiguration in the Razorpay dashboard. The webhook
 * (PaymentWebhookController/PaymentWebhookEventService) remains the primary, low-latency
 * path — this is the backstop, not a replacement for it, so it deliberately runs on a
 * slow poll rather than tightening the loop.
 *
 * PENDING invoices only reach this sweep once they're past RECONCILE_GRACE_MINUTES old,
 * so an in-progress checkout (client mid-payment, browser tab still open) is never raced
 * by a background reconciliation pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationService {

    private static final int RECONCILE_GRACE_MINUTES = 15;

    private final InvoiceRepository invoiceRepository;
    private final RazorpayClient razorpayClient;
    private final InvoiceService invoiceService;

    @Scheduled(fixedDelayString = "${app.razorpay.reconciliation-interval-ms:1200000}") // 20 min default
    public void reconcilePendingInvoices() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RECONCILE_GRACE_MINUTES);
        List<Invoice> candidates = invoiceRepository
                .findByStatusAndRazorpayOrderIdIsNotNullAndCreatedAtBefore(InvoiceStatus.PENDING, cutoff);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Reconciliation sweep: checking {} pending invoice(s) against Razorpay", candidates.size());
        int healed = 0;
        for (Invoice invoice : candidates) {
            try {
                if (reconcileOne(invoice)) {
                    healed++;
                }
            } catch (RazorpayException e) {
                // One bad lookup shouldn't abort the whole sweep — log and move on to the
                // next candidate; this invoice gets picked up again on the next run.
                log.warn("Reconciliation lookup failed for invoice {} (order {}): {}",
                        invoice.getInvoiceNumber(), invoice.getRazorpayOrderId(), e.getMessage());
            }
        }
        if (healed > 0) {
            log.info("Reconciliation sweep: self-healed {} invoice(s) to PAID", healed);
        }
    }

    /**
     * Fetches every payment attempt Razorpay has recorded against this order and, if any
     * is "captured", reuses InvoiceService.markPaidFromWebhook — the exact same
     * state-transition path the real webhook uses — so a reconciled invoice ends up in
     * an identical state to one confirmed live, with no separate code path to drift out
     * of sync.
     */
    @Transactional
    boolean reconcileOne(Invoice invoice) throws RazorpayException {
        List<Payment> payments = razorpayClient.orders.fetchPayments(invoice.getRazorpayOrderId());
        for (Payment payment : payments) {
            JSONObject json = payment.toJson();
            String status = json.optString("status", "");
            if ("captured".equals(status)) {
                String paymentId = json.optString("id", null);
                invoiceService.markPaidFromWebhook(invoice.getRazorpayOrderId(), paymentId, PaymentSource.RECONCILIATION);
                log.info("Invoice {} self-healed to PAID via reconciliation sweep (payment {})",
                        invoice.getInvoiceNumber(), paymentId);
                return true;
            }
        }
        return false;
    }
}
