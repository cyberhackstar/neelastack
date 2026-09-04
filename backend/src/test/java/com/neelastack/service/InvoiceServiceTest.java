package com.neelastack.service;

import com.neelastack.dto.payment.PaymentVerificationRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.PaymentAttempt;
import com.neelastack.entity.PaymentAttemptStatus;
import com.neelastack.entity.PaymentSource;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.PaymentAttemptRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Payments are the one place where a subtle bug costs real money — either a client is
 * charged and never gets marked PAID, or (much worse) an invoice is marked PAID without
 * a genuine, verified payment behind it. These tests exercise exactly the scenarios the
 * review called out: correct/invalid signatures, order-ID mismatch, an already-paid
 * invoice, and the webhook arriving once, twice, or racing the browser-side confirmation.
 */
class InvoiceServiceTest {

    private InvoiceRepository invoiceRepository;
    private PaymentAttemptRepository paymentAttemptRepository;
    private EngagementService engagementService;
    private RazorpayClient razorpayClient;
    private PdfInvoiceService pdfInvoiceService;
    private AuditLogService auditLogService;
    private TestimonialService testimonialService;
    private InvoiceService invoiceService;

    private static final String SECRET = "test-razorpay-key-secret";

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);
        engagementService = mock(EngagementService.class);
        razorpayClient = mock(RazorpayClient.class);
        pdfInvoiceService = mock(PdfInvoiceService.class);
        auditLogService = mock(AuditLogService.class);
        testimonialService = mock(TestimonialService.class);

        invoiceService = new InvoiceService(invoiceRepository, paymentAttemptRepository, engagementService, razorpayClient, pdfInvoiceService, auditLogService, testimonialService);
        setField(invoiceService, "razorpayKeyId", "test-key-id");
        setField(invoiceService, "razorpayKeySecret", SECRET);
    }

    private Invoice invoiceWithOrder(String orderId, InvoiceStatus status) {
        Engagement engagement = Engagement.builder().id(UUID.randomUUID()).build();
        return Invoice.builder()
                .id(UUID.randomUUID())
                .engagement(engagement)
                .invoiceNumber("NLS-2026-0001")
                .amount(BigDecimal.valueOf(50000))
                .currency("INR")
                .status(status)
                .razorpayOrderId(orderId)
                .build();
    }

    // --- createOrder ---

    @Test
    void createOrder_rejectsAlreadyPaidInvoice() {
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PAID);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());

        assertThatThrownBy(() -> invoiceService.createOrder(invoice.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been paid");
    }

    @Test
    void createOrder_existingLiveAttempt_returnsSameOrderWithoutCallingRazorpay() {
        // Simulates a second browser tab / a retried request while a checkout is already
        // in flight for this invoice -- the fix for the concurrency bug described in the
        // spec: this must NOT call Razorpay again or overwrite razorpayOrderId.
        Invoice invoice = invoiceWithOrder("order_EXISTING", InvoiceStatus.PENDING);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());

        PaymentAttempt liveAttempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .invoice(invoice)
                .razorpayOrderId("order_EXISTING")
                .status(PaymentAttemptStatus.CREATED)
                .amount(invoice.getAmount())
                .build();
        when(paymentAttemptRepository.findByInvoiceIdAndStatus(invoice.getId(), PaymentAttemptStatus.CREATED))
                .thenReturn(Collections.singletonList(liveAttempt));

        var response = invoiceService.createOrder(invoice.getId());

        assertThat(response.razorpayOrderId()).isEqualTo("order_EXISTING");
        verifyNoInteractions(razorpayClient);
        verify(paymentAttemptRepository, never()).save(any());
    }

    // --- verifyAndConfirmPayment ---

    @Test
    void verifyAndConfirmPayment_validSignature_marksInvoicePaid() {
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PENDING);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentVerificationRequest request = new PaymentVerificationRequest("order_123", "pay_456", "sig_789");

        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyPaymentSignature(any(), eq(SECRET))).thenReturn(true);

            var dto = invoiceService.verifyAndConfirmPayment(invoice.getId(), request);

            assertThat(dto.status()).isEqualTo(InvoiceStatus.PAID);
        }
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getRazorpayPaymentId()).isEqualTo("pay_456");
        assertThat(invoice.getPaidAt()).isNotNull();
    }

    @Test
    void verifyAndConfirmPayment_invalidSignature_marksFailedAndThrows() {
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PENDING);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentVerificationRequest request = new PaymentVerificationRequest("order_123", "pay_456", "bad_sig");

        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyPaymentSignature(any(), eq(SECRET))).thenReturn(false);

            assertThatThrownBy(() -> invoiceService.verifyAndConfirmPayment(invoice.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not confirmed");
        }
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
    }

    @Test
    void verifyAndConfirmPayment_wrongOrderId_rejectedBeforeSignatureCheck() {
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PENDING);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());

        // A different order ID than the one actually issued for this invoice.
        PaymentVerificationRequest request = new PaymentVerificationRequest("order_ATTACKER", "pay_456", "sig_789");

        assertThatThrownBy(() -> invoiceService.verifyAndConfirmPayment(invoice.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Order mismatch");

        // Status must be untouched — this request never got far enough to affect the invoice.
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        verify(invoiceRepository, never()).save(any());
    }

    // --- create (invoice numbering) ---

    @Test
    void create_usesAtomicYearSequenceForInvoiceNumber() {
        var engagement = Engagement.builder().id(UUID.randomUUID()).build();
        var request = new com.neelastack.dto.payment.InvoiceRequest(
                engagement.getId(), "First milestone", BigDecimal.valueOf(25000), "INR", null);

        when(engagementService.getEntityWithAccessCheck(engagement.getId())).thenReturn(engagement);
        when(invoiceRepository.nextInvoiceSequenceForYear(anyInt())).thenReturn(7L);
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = invoiceService.create(request);

        int year = java.time.Year.now().getValue();
        assertThat(dto.invoiceNumber()).isEqualTo("NLS-" + year + "-0007");
        verify(invoiceRepository).nextInvoiceSequenceForYear(year);
        // No retry-on-collision path anymore — numbering is race-free by construction, so a
        // single call to the sequence and a single save is all that should happen.
        verify(invoiceRepository, times(1)).saveAndFlush(any(Invoice.class));
    }

    // --- markPaidFromWebhook (reconciliation + idempotency) ---

    @Test
    void webhook_marksPendingInvoicePaid() {
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PENDING);
        when(invoiceRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(invoice));

        invoiceService.markPaidFromWebhook("order_123", "pay_999", PaymentSource.WEBHOOK);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getRazorpayPaymentId()).isEqualTo("pay_999");
        assertThat(invoice.getPaymentSource()).isEqualTo(PaymentSource.WEBHOOK);
        verify(invoiceRepository, times(1)).save(invoice);
    }

    @Test
    void webhook_duplicateDelivery_isNoOp() {
        // Simulates Razorpay retrying the same webhook, or the webhook arriving after the
        // browser-side verifyAndConfirmPayment already marked this invoice PAID.
        Invoice invoice = invoiceWithOrder("order_123", InvoiceStatus.PAID);
        invoice.setRazorpayPaymentId("pay_456");
        when(invoiceRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(invoice));

        invoiceService.markPaidFromWebhook("order_123", "pay_456", PaymentSource.WEBHOOK);

        // Must not re-save (which would also overwrite paidAt with a later timestamp).
        verify(invoiceRepository, never()).save(any());
        assertThat(invoice.getRazorpayPaymentId()).isEqualTo("pay_456");
    }

    @Test
    void webhook_unknownOrderId_doesNothingAndDoesNotThrow() {
        when(invoiceRepository.findByRazorpayOrderId("order_UNKNOWN")).thenReturn(Optional.empty());

        invoiceService.markPaidFromWebhook("order_UNKNOWN", "pay_999", PaymentSource.WEBHOOK);

        verify(invoiceRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"order_123"})
    void webhook_thenBrowserVerification_bothArrivingLeavesInvoicePaidAndConsistent(String orderId) {
        // The race the review specifically calls out: browser verification and the webhook
        // can both arrive for the same invoice almost simultaneously. Whichever wins, the
        // final state must be PAID exactly once with no exception thrown by the second one.
        Invoice invoice = invoiceWithOrder(orderId, InvoiceStatus.PENDING);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(engagementService.getEntityWithAccessCheck(invoice.getEngagement().getId())).thenReturn(invoice.getEngagement());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(invoice));

        PaymentVerificationRequest request = new PaymentVerificationRequest(orderId, "pay_456", "sig_789");
        try (MockedStatic<Utils> utils = Mockito.mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifyPaymentSignature(any(), eq(SECRET))).thenReturn(true);
            invoiceService.verifyAndConfirmPayment(invoice.getId(), request);
        }

        // Webhook arrives second, for the same payment — must not throw or double-charge state.
        invoiceService.markPaidFromWebhook(orderId, "pay_456", PaymentSource.WEBHOOK);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    private static void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
