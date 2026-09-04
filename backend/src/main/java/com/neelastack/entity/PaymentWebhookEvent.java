package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per Razorpay webhook delivery, keyed on the provider's own X-Razorpay-Event-Id.
 * Razorpay explicitly documents that a webhook may be delivered more than once for the same
 * event (retries on non-2xx, or just duplicate sends) and that the event-id header is the
 * supported way to detect that. Persisting every delivery — not just recognizing duplicates
 * in memory — also gives us an audit trail of exactly what Razorpay told us and when.
 */
@Entity
@Table(name = "payment_webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "razorpay_event_id", nullable = false, unique = true, length = 100)
    private String razorpayEventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WebhookEventStatus status = WebhookEventStatus.RECEIVED;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /** Incremented each time this event is (re)claimed for processing — visibility only, see V13. */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 1;

    public enum WebhookEventStatus {
        RECEIVED, PROCESSED, IGNORED, FAILED
    }
}
