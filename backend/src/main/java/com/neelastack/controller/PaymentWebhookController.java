package com.neelastack.controller;

import com.neelastack.service.PaymentWebhookEventService;
import com.neelastack.service.PaymentWebhookProcessor;
import com.razorpay.Utils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server webhook from Razorpay. This is the reconciliation backstop:
 * even if the client's browser never returns from checkout (closed tab, network
 * drop), the payment still gets marked PAID once Razorpay confirms it here.
 *
 * Configure this URL in the Razorpay dashboard under Settings -> Webhooks,
 * pointed at https://neelastack.com/api/v1/payments/webhook, subscribed to
 * the "payment.captured" event, using the same secret as RAZORPAY_WEBHOOK_SECRET.
 *
 * Idempotency + retry semantics (see PaymentWebhookEventService for the full design):
 * Razorpay explicitly documents that the same event can be delivered more than once — both
 * as a deliberate retry on any non-2xx response, and as an occasional duplicate send even on
 * success — and that X-Razorpay-Event-Id is the header to de-duplicate on. "Already recorded"
 * only means "already handled" once its status is PROCESSED: a delivery whose processing
 * previously FAILED is NOT swallowed as a duplicate. This handler returns a 5xx for a FAILED
 * outcome specifically so Razorpay's own retry mechanism tries the event again, and
 * PaymentWebhookEventService reclaims it instead of skipping it forever. Without this, a
 * transient failure (a DB hiccup, a momentary outage) could permanently strand an invoice as
 * unpaid despite a real, successful payment.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment webhook", description = "Public — authenticated via Razorpay webhook signature, not a bearer token")
public class PaymentWebhookController {

    private final PaymentWebhookEventService webhookEventService;
    private final PaymentWebhookProcessor webhookProcessor;

    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/api/v1/payments/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String rawBody,
                                                  @RequestHeader("X-Razorpay-Signature") String signature,
                                                  @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        try {
            boolean valid = Utils.verifyWebhookSignature(rawBody, signature, webhookSecret);
            if (!valid) {
                log.warn("Rejected webhook call with invalid signature");
                return ResponseEntity.status(400).body("invalid signature");
            }
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return ResponseEntity.status(400).body("verification error");
        }

        if (eventId == null || eventId.isBlank()) {
            // Should never happen for a genuine Razorpay delivery — every event carries this
            // header — but without it we have no reliable de-duplication key, so treat it as
            // untrustworthy rather than silently processing it without idempotency protection.
            log.warn("Rejected webhook call with missing X-Razorpay-Event-Id header");
            return ResponseEntity.status(400).body("missing event id");
        }

        JSONObject event = new JSONObject(rawBody);
        String eventType = event.optString("event");

        String paymentId = null;
        String orderId = null;
        if (event.has("payload") && event.getJSONObject("payload").has("payment")) {
            JSONObject payment = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
            paymentId = payment.optString("id", null);
            orderId = payment.optString("order_id", null);
        }

        var result = webhookEventService.handleDelivery(eventId, eventType, paymentId, orderId, rawBody, webhookProcessor::process);

        return switch (result.outcome()) {
            case DUPLICATE -> {
                log.info("Not processing webhook event {} — already handled or being handled elsewhere", eventId);
                yield ResponseEntity.ok("ok (duplicate, already handled)");
            }
            case PROCESSED -> ResponseEntity.ok("ok");
            // 5xx, deliberately — this is what makes Razorpay retry the same event id instead
            // of considering it delivered. See the class-level note on retry semantics.
            case FAILED -> ResponseEntity.status(500).body("processing failed, please retry");
        };
    }
}
