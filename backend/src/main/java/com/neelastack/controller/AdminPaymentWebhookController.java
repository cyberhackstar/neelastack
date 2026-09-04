package com.neelastack.controller;

import com.neelastack.dto.payment.PaymentWebhookEventDto;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.PaymentWebhookEvent.WebhookEventStatus;
import com.neelastack.service.AuditLogService;
import com.neelastack.service.PaymentWebhookEventService;
import com.neelastack.service.PaymentWebhookProcessor;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Operational visibility and control over persisted webhook deliveries (review item #3):
 * the automatic retry path in PaymentWebhookController handles Razorpay's own retries, but an
 * admin still needs to see what's sitting in FAILED (or stuck) and manually replay a specific
 * one — e.g. after fixing whatever caused the failure, or investigating a payment a client
 * says went through but the site still shows as pending.
 */
@RestController
@RequestMapping("/api/v1/admin/payments/webhook-events")
@RequiredArgsConstructor
@Tag(name = "Admin — payment webhook events", description = "Requires ROLE_ADMIN")
public class AdminPaymentWebhookController {

    private final PaymentWebhookEventService webhookEventService;
    private final PaymentWebhookProcessor webhookProcessor;
    private final AuditLogService auditLogService;

    @GetMapping
    public Page<PaymentWebhookEventDto> list(@RequestParam(required = false) WebhookEventStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return webhookEventService.list(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt")));
    }

    /**
     * Re-runs processing for one event, exactly as if Razorpay had just redelivered it — same
     * PaymentWebhookProcessor, same status transitions, same row-locked
     * claim/process/mark-outcome sequence as the live endpoint. Refuses (400) if the event is
     * already PROCESSED; nothing to replay.
     */
    @PostMapping("/{id}/replay")
    public ResponseEntity<PaymentWebhookEventDto> replay(@PathVariable UUID id) {
        var result = webhookEventService.replay(id, webhookProcessor::process);
        auditLogService.recordBestEffort(AuditAction.WEBHOOK_REPLAYED, "PaymentWebhookEvent", id.toString(),
                Map.of("resultStatus", String.valueOf(result.event().getStatus())));
        return ResponseEntity.ok(PaymentWebhookEventDto.from(result.event()));
    }
}
