package com.neelastack.dto.payment;

import com.neelastack.entity.PaymentWebhookEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin-facing view of a persisted webhook delivery (see AdminPaymentWebhookController). Omits the raw payload to keep list responses light. */
public record PaymentWebhookEventDto(
        UUID id,
        String razorpayEventId,
        String eventType,
        String razorpayPaymentId,
        String razorpayOrderId,
        PaymentWebhookEvent.WebhookEventStatus status,
        int attemptCount,
        LocalDateTime receivedAt,
        LocalDateTime processedAt
) {
    public static PaymentWebhookEventDto from(PaymentWebhookEvent e) {
        return new PaymentWebhookEventDto(
                e.getId(), e.getRazorpayEventId(), e.getEventType(), e.getRazorpayPaymentId(),
                e.getRazorpayOrderId(), e.getStatus(), e.getAttemptCount(), e.getReceivedAt(), e.getProcessedAt());
    }
}
