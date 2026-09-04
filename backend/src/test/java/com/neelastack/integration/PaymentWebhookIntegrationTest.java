package com.neelastack.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.EngagementStatus;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.PaymentWebhookEvent;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.PaymentWebhookEventRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises /api/v1/payments/webhook and the admin replay endpoint end to end — the "webhook
 * replay" and "payment verification" HTTP-level coverage the review specifically listed as
 * missing (item #5). Uses the real signature-verification code path (a genuine HMAC-SHA256
 * over the raw body, same as Razorpay computes), not a mocked signature check.
 */
@TestPropertySource(properties = "app.razorpay.webhook-secret=" + PaymentWebhookIntegrationTest.WEBHOOK_SECRET)
class PaymentWebhookIntegrationTest extends AbstractIntegrationTest {

    static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String PASSWORD = "Str0ngPassw0rd!";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private EngagementRepository engagementRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentWebhookEventRepository webhookEventRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** Creates a client user (via the real register endpoint) plus an engagement and a PENDING invoice tied to orderId. */
    private Invoice pendingInvoiceForOrder(String orderId) throws Exception {
        String email = "webhook-" + orderId + "@example.com";
        String payload = """
                {"fullName":"Webhook Test","email":"%s","password":"%s","phone":"9999999999"}
                """.formatted(email, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        User client = userRepository.findByEmail(email).orElseThrow();
        Engagement engagement = engagementRepository.save(Engagement.builder()
                .client(client)
                .title("Webhook test engagement")
                .status(EngagementStatus.ONBOARDING)
                .build());

        return invoiceRepository.save(Invoice.builder()
                .engagement(engagement)
                .invoiceNumber("NLS-2026-" + orderId)
                .description("Webhook test invoice")
                .amount(BigDecimal.valueOf(10000))
                .currency("INR")
                .status(InvoiceStatus.PENDING)
                .razorpayOrderId(orderId)
                .build());
    }

    /** Creates an admin user directly (mirrors AuthorizationIntegrationTest's pattern) and returns a bearer access token for it. */
    private String adminAccessToken(String email) throws Exception {
        User admin = User.builder()
                .fullName("Test Admin")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.ADMIN)
                .enabled(true)
                .emailVerified(true)
                .build();
        userRepository.save(admin);

        String loginPayload = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String capturedPayload(String orderId, String paymentId) {
        return """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s"}}}}
                """.formatted(paymentId, orderId);
    }

    @Test
    void invalidSignature_returns400() throws Exception {
        String payload = capturedPayload("order_bad_sig", "pay_1");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "not-a-real-signature")
                        .header("X-Razorpay-Event-Id", "evt_bad_sig")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingEventIdHeader_returns400() throws Exception {
        String payload = capturedPayload("order_no_evt", "pay_1");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPaymentCaptured_marksInvoicePaidAndRecordsEventAsProcessed() throws Exception {
        Invoice invoice = pendingInvoiceForOrder("order_ok_1");
        String payload = capturedPayload("order_ok_1", "pay_ok_1");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(payload))
                        .header("X-Razorpay-Event-Id", "evt_ok_1")
                        .content(payload))
                .andExpect(status().isOk());

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(reloaded.getRazorpayPaymentId()).isEqualTo("pay_ok_1");

        PaymentWebhookEvent event = webhookEventRepository.findByRazorpayEventId("evt_ok_1").orElseThrow();
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookEvent.WebhookEventStatus.PROCESSED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void duplicateDelivery_ofAlreadyProcessedEvent_isNoOp() throws Exception {
        Invoice invoice = pendingInvoiceForOrder("order_dup_1");
        String payload = capturedPayload("order_dup_1", "pay_dup_1");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/payments/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Razorpay-Signature", sign(payload))
                            .header("X-Razorpay-Event-Id", "evt_dup_1")
                            .content(payload))
                    .andExpect(status().isOk());
        }

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Must not have been reprocessed a second time — attemptCount stays at 1, and the row
        // is still the single one created on first delivery.
        PaymentWebhookEvent event = webhookEventRepository.findByRazorpayEventId("evt_dup_1").orElseThrow();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void adminReplay_ofFailedEvent_reprocessesAndMarksProcessed() throws Exception {
        // Simulates the operational scenario the review asked for (item #3): an event that
        // previously FAILED (here, seeded directly rather than forced through a real failure)
        // gets manually replayed by an admin and succeeds this time.
        Invoice invoice = pendingInvoiceForOrder("order_replay_1");
        PaymentWebhookEvent failedEvent = webhookEventRepository.save(PaymentWebhookEvent.builder()
                .razorpayEventId("evt_replay_1")
                .eventType("payment.captured")
                .razorpayPaymentId("pay_replay_1")
                .razorpayOrderId("order_replay_1")
                .rawPayload(capturedPayload("order_replay_1", "pay_replay_1"))
                .status(PaymentWebhookEvent.WebhookEventStatus.FAILED)
                .attemptCount(1)
                .receivedAt(LocalDateTime.now())
                .build());

        String body = mockMvc.perform(post("/api/v1/admin/payments/webhook-events/" + failedEvent.getId() + "/replay")
                        .header("Authorization", "Bearer " + adminAccessToken("replay-admin-1@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("status").asText()).isEqualTo("PROCESSED");
        assertThat(json.get("attemptCount").asInt()).isEqualTo(2);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void adminReplay_ofAlreadyProcessedEvent_isRejected() throws Exception {
        PaymentWebhookEvent processedEvent = webhookEventRepository.save(PaymentWebhookEvent.builder()
                .razorpayEventId("evt_already_done")
                .eventType("payment.captured")
                .razorpayOrderId("order_already_done")
                .rawPayload("{}")
                .status(PaymentWebhookEvent.WebhookEventStatus.PROCESSED)
                .attemptCount(1)
                .receivedAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/admin/payments/webhook-events/" + processedEvent.getId() + "/replay")
                        .header("Authorization", "Bearer " + adminAccessToken("replay-admin-2@example.com")))
                .andExpect(status().isBadRequest());
    }
}
