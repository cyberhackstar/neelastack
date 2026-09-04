package com.neelastack.service;

import com.neelastack.dto.payment.CreateOrderResponse;
import com.neelastack.dto.payment.InvoiceDto;
import com.neelastack.dto.payment.InvoiceRequest;
import com.neelastack.dto.payment.PaymentVerificationRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.PaymentAttempt;
import com.neelastack.entity.PaymentAttemptStatus;
import com.neelastack.entity.PaymentSource;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.PaymentAttemptRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final EngagementService engagementService;
    private final RazorpayClient razorpayClient;
    private final PdfInvoiceService pdfInvoiceService;
    private final AuditLogService auditLogService;
    private final TestimonialService testimonialService;

    @Value("${app.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    // Razorpay order objects themselves have a short validity window (standard orders expire
    // in a matter of hours). A CREATED payment_attempt older than this is treated as stale —
    // a customer who abandoned checkout and returns days later must get a fresh order rather
    // than the same possibly-expired one handed back indefinitely.
    @Value("${app.razorpay.order-ttl-minutes:60}")
    private long razorpayOrderTtlMinutes;

    @Transactional
    public InvoiceDto create(InvoiceRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(request.engagementId());

        // nextInvoiceNumber() gets its number from an atomic UPSERT against a per-year
        // counter row, so concurrent invoice creation is serialized by Postgres's row lock
        // on that counter — no collision is possible here, and therefore no retry loop is
        // needed the way the old count(*)-based scheme required.
        Invoice invoice = Invoice.builder()
                .engagement(engagement)
                .invoiceNumber(nextInvoiceNumber())
                .description(request.description())
                .amount(request.amount())
                .currency(request.currency() != null ? request.currency() : "INR")
                .status(InvoiceStatus.PENDING)
                .dueDate(request.dueDate())
                .build();

        return toDto(invoiceRepository.saveAndFlush(invoice));
    }

    public List<InvoiceDto> listForEngagement(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return invoiceRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Creates (or reuses) a Razorpay order for this invoice's checkout.
     *
     * Concurrency fix: the previous version fetched the invoice with a plain (unlocked) read,
     * so two nearly-simultaneous requests -- two browser tabs, a double-click, a client retry
     * racing a slow first response -- could both pass the PAID check, both call Razorpay, and
     * both overwrite {@code invoice.razorpayOrderId} in turn. The second write silently
     * orphaned the first order: if the *first* tab's checkout popup completed payment against
     * the order id it was actually shown, {@code verifyAndConfirmPayment} would reject it as an
     * "order mismatch" because the invoice row now pointed at the second tab's order id --
     * a paying customer's transaction failing to reconcile.
     *
     * Fixed by: (1) a pessimistic row lock on the invoice for the duration of this method, so
     * a second concurrent call blocks until the first has committed; (2) re-checking PAID
     * status *after* acquiring the lock, not just before; (3) checking for an existing live
     * (CREATED) payment attempt for this invoice and returning it unchanged instead of minting
     * a second Razorpay order when one is already in flight.
     */
    @Transactional
    public CreateOrderResponse createOrder(UUID invoiceId) {
        // Access check first, against an unlocked read -- getEntityWithAccessCheck may itself
        // run queries, and holding the invoice's row lock across that isn't necessary for
        // correctness here (the invoice can't disappear or change engagement mid-flight).
        getInvoiceWithAccessCheck(invoiceId);

        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("This invoice has already been paid");
        }

        // A checkout already in flight for this invoice -- hand the same order back rather than
        // creating a second one, UNLESS it's aged past the TTL a Razorpay order stays valid for
        // (see razorpayOrderTtlMinutes) -- an expired order id would fail at Razorpay's end
        // anyway, so silently returning it just delays the customer hitting a checkout error.
        List<PaymentAttempt> liveAttempts =
                paymentAttemptRepository.findByInvoiceIdAndStatus(invoiceId, PaymentAttemptStatus.CREATED);
        if (!liveAttempts.isEmpty()) {
            PaymentAttempt live = liveAttempts.get(0);
            boolean expired = live.getCreatedAt() != null
                    && live.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(razorpayOrderTtlMinutes));
            if (!expired) {
                long amountInPaiseExisting = invoice.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();
                return CreateOrderResponse.builder()
                        .razorpayOrderId(live.getRazorpayOrderId())
                        .razorpayKeyId(razorpayKeyId)
                        .amountInPaise(amountInPaiseExisting)
                        .currency(invoice.getCurrency())
                        .invoiceNumber(invoice.getInvoiceNumber())
                        .description(invoice.getDescription())
                        .build();
            }
            // Falls through to mint a fresh order below; the stale row is superseded by the
            // supersedeLiveAttempts(invoiceId) call that already runs just before the insert.
            log.info("Payment attempt {} for invoice {} is past the {}-minute order TTL — creating a fresh order",
                    live.getId(), invoiceId, razorpayOrderTtlMinutes);
        }

        long amountInPaise = invoice.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", invoice.getCurrency());
            orderRequest.put("receipt", invoice.getInvoiceNumber());
            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            // Belt-and-suspenders: supersede any stale CREATED rows left behind by a crash
            // between order creation and the insert below on a prior attempt (there shouldn't
            // be any live ones here since we just checked, but this keeps the invariant --
            // "at most one CREATED attempt per invoice" -- true even after a partial failure.
            paymentAttemptRepository.supersedeLiveAttempts(invoiceId);

            paymentAttemptRepository.save(PaymentAttempt.builder()
                    .invoice(invoice)
                    .razorpayOrderId(razorpayOrderId)
                    .status(PaymentAttemptStatus.CREATED)
                    .amount(invoice.getAmount())
                    .build());

            invoice.setRazorpayOrderId(razorpayOrderId);
            invoiceRepository.save(invoice);

            return CreateOrderResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .razorpayKeyId(razorpayKeyId)
                    .amountInPaise(amountInPaise)
                    .currency(invoice.getCurrency())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .description(invoice.getDescription())
                    .build();
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            throw new BadRequestException("Could not initiate payment — please try again shortly");
        }
    }

    /**
     * Fixed a real race between this method and both the webhook (markPaidFromWebhook) and
     * a second call to this same method: without a lock, a webhook confirming payment could
     * race a slower/duplicate browser verification call, and — worse — an invalid-signature
     * branch could unconditionally overwrite the invoice to FAILED even after a PAID webhook
     * had already landed, regressing a successfully paid invoice. Fixed the same way as
     * createOrder: (1) a pessimistic row lock for the duration of this method, (2) re-check
     * PAID status after acquiring the lock and return the existing paid invoice idempotently
     * instead of reprocessing, (3) never transition a PAID invoice to FAILED.
     */
    @Transactional
    public InvoiceDto verifyAndConfirmPayment(UUID invoiceId, PaymentVerificationRequest request) {
        // Access check first against an unlocked read, same rationale as createOrder.
        getInvoiceWithAccessCheck(invoiceId);

        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            // Already confirmed — by the webhook, or by a prior/duplicate call to this same
            // method. Idempotent success rather than re-verifying or, worse, letting a stale
            // duplicate request downgrade it.
            return toDto(invoice);
        }

        if (invoice.getRazorpayOrderId() == null || !invoice.getRazorpayOrderId().equals(request.razorpayOrderId())) {
            throw new BadRequestException("Order mismatch — please retry the payment");
        }

        JSONObject payload = new JSONObject();
        payload.put("razorpay_order_id", request.razorpayOrderId());
        payload.put("razorpay_payment_id", request.razorpayPaymentId());
        payload.put("razorpay_signature", request.razorpaySignature());

        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(payload, razorpayKeySecret);
        } catch (RazorpayException e) {
            throw new BadRequestException("Payment signature verification failed");
        }

        if (!valid) {
            // Re-check under lock: a webhook could have marked this PAID between the check
            // above and this point (it takes its own lock via invoiceRepository, so this
            // read is safe once we hold ours). Never let an invalid client-side signature
            // regress an invoice a webhook already confirmed as paid.
            if (invoice.getStatus() != InvoiceStatus.PAID) {
                invoice.setStatus(InvoiceStatus.FAILED);
                invoiceRepository.save(invoice);
                paymentAttemptRepository.findByRazorpayOrderId(request.razorpayOrderId())
                        .ifPresent(attempt -> {
                            attempt.setStatus(PaymentAttemptStatus.FAILED);
                            paymentAttemptRepository.save(attempt);
                        });
            }
            throw new BadRequestException("Payment signature is invalid — this payment was not confirmed");
        }

        invoice.setRazorpayPaymentId(request.razorpayPaymentId());
        invoice.setRazorpaySignature(request.razorpaySignature());
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        // paymentSource intentionally left null here: this is the direct client-verified
        // path (signature checked synchronously against the client's own callback), not
        // the webhook or the reconciliation sweep -- the two values PaymentSource models.
        // Tagging it as either would misattribute how this invoice actually got marked paid.

        InvoiceDto dto = toDto(invoiceRepository.save(invoice));

        paymentAttemptRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .ifPresent(attempt -> {
                    attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
                    paymentAttemptRepository.save(attempt);
                });

        // Module 4 (Client Acquisition & High-Ticket Conversion Engine): queue the
        // automated testimonial request now that this invoice has genuinely transitioned
        // to PAID. Best-effort inside TestimonialService — never allowed to affect this
        // (already-committed-in-intent) payment confirmation.
        testimonialService.queueRequestForInvoice(invoice);

        return dto;
    }

    /**
     * Called from the Razorpay webhook as a reconciliation backstop, independent of the
     * client-side flow. Takes the same pessimistic row lock as createOrder/
     * verifyAndConfirmPayment so this can never race either of them into an inconsistent
     * state (e.g. marking paid while a browser verification call is mid-flight).
     */
    @Transactional
    public void markPaidFromWebhook(String razorpayOrderId, String razorpayPaymentId, PaymentSource source) {
        invoiceRepository.findByRazorpayOrderIdForUpdate(razorpayOrderId).ifPresent(invoice -> {
            if (invoice.getStatus() != InvoiceStatus.PAID) {
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setRazorpayPaymentId(razorpayPaymentId);
                invoice.setPaidAt(LocalDateTime.now());
                invoice.setPaymentSource(source);
                invoiceRepository.save(invoice);
                paymentAttemptRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(attempt -> {
                    attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
                    paymentAttemptRepository.save(attempt);
                });
                log.info("Invoice {} marked PAID via {}", invoice.getInvoiceNumber(), source);
                auditLogService.recordBestEffort(AuditAction.PAYMENT_MARKED_PAID, "Invoice", invoice.getId().toString(),
                        Map.of("source", source.name(), "razorpayPaymentId", razorpayPaymentId == null ? "" : razorpayPaymentId));
                testimonialService.queueRequestForInvoice(invoice);
            }
        });
    }

    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = getInvoiceWithAccessCheck(invoiceId);
        return pdfInvoiceService.generate(invoice);
    }

    private Invoice getInvoiceWithAccessCheck(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        // Reuses engagement ownership check (throws AccessDeniedException if the caller can't see it)
        engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId());
        return invoice;
    }

    private String nextInvoiceNumber() {
        int year = Year.now().getValue();
        long sequence = invoiceRepository.nextInvoiceSequenceForYear(year);
        return "NLS-" + year + "-" + String.format("%04d", sequence);
    }

    private InvoiceDto toDto(Invoice i) {
        return InvoiceDto.builder()
                .id(i.getId())
                .engagementId(i.getEngagement().getId())
                .invoiceNumber(i.getInvoiceNumber())
                .description(i.getDescription())
                .amount(i.getAmount())
                .currency(i.getCurrency())
                .status(i.getStatus())
                .dueDate(i.getDueDate())
                .paidAt(i.getPaidAt())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
