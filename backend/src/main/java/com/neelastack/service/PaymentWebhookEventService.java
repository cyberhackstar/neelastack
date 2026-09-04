package com.neelastack.service;

import com.neelastack.dto.payment.PaymentWebhookEventDto;
import com.neelastack.entity.PaymentWebhookEvent;
import com.neelastack.entity.PaymentWebhookEvent.WebhookEventStatus;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns the full lifecycle of a persisted Razorpay webhook delivery: claiming an event id for
 * processing, running the caller-supplied handler, and recording the outcome — all inside one
 * transaction that holds a row lock on that event's record the entire time.
 *
 * Why the lock has to span the whole thing, not just the initial claim: an earlier version of
 * this class released the lock right after marking an event RECEIVED, then ran the handler and
 * recorded the outcome in separate transactions. That left a window where a second, genuinely
 * concurrent delivery of the same event id could see status=RECEIVED (because the first
 * delivery hadn't reached PROCESSED/FAILED yet) and wrongly conclude it was a crashed/stuck
 * event safe to reclaim — both deliveries would then process it. Holding the lock for the
 * entire claim+process+mark sequence forces a second claimant to block until the first one has
 * actually committed a terminal status, so it sees the real outcome instead of a stale
 * in-progress one.
 *
 * Idempotency + retry semantics: only PROCESSED is a terminal, skip-forever state. FAILED (or
 * a row stuck at RECEIVED from a crash) is reclaimed and reprocessed — see
 * PaymentWebhookController's class-level note on why it returns 5xx for a FAILED outcome so
 * Razorpay's own retry mechanism drives that reprocessing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookEventService {

    private final PaymentWebhookEventRepository webhookEventRepository;

    public enum Outcome { DUPLICATE, PROCESSED, FAILED }

    /** event is null for DUPLICATE only when this call lost a race to claim a first-time delivery — nothing more to report about it. */
    public record WebhookOutcome(PaymentWebhookEvent event, Outcome outcome) {}

    /**
     * Claims eventId (creating its record if this is the first delivery, reclaiming it if it
     * previously FAILED or is stuck at RECEIVED), runs handler against it, and records
     * PROCESSED/IGNORED or FAILED depending on whether handler throws. Returns DUPLICATE
     * without invoking handler if the event already reached PROCESSED, or if this call lost a
     * race to claim a first-time delivery.
     */
    @Transactional
    public WebhookOutcome handleDelivery(String eventId, String eventType, String paymentId, String orderId,
                                          String rawBody, Consumer<PaymentWebhookEvent> handler) {
        var existing = webhookEventRepository.findByRazorpayEventIdForUpdate(eventId);
        PaymentWebhookEvent event;

        if (existing.isPresent()) {
            event = existing.get();
            if (event.getStatus() == WebhookEventStatus.PROCESSED) {
                return new WebhookOutcome(event, Outcome.DUPLICATE);
            }
            event.setStatus(WebhookEventStatus.RECEIVED);
            event.setAttemptCount(event.getAttemptCount() + 1);
        } else {
            try {
                event = webhookEventRepository.saveAndFlush(PaymentWebhookEvent.builder()
                        .razorpayEventId(eventId)
                        .eventType(eventType)
                        .razorpayPaymentId(paymentId)
                        .razorpayOrderId(orderId)
                        .rawPayload(rawBody)
                        .status(WebhookEventStatus.RECEIVED)
                        .build());
            } catch (DataIntegrityViolationException e) {
                // Lost the race with a concurrent first-time delivery of the same event id —
                // that delivery owns processing it.
                return webhookEventRepository.findByRazorpayEventId(eventId)
                        .map(ev -> new WebhookOutcome(ev, Outcome.DUPLICATE))
                        .orElseThrow(() -> e);
            }
        }

        return runAndRecord(event, eventType, handler);
    }

    /**
     * Admin manual replay (review item #3): re-runs handler for a specific event by id.
     * Refuses to replay an event that's already PROCESSED — that would mean running
     * payment-confirmation side effects a second time for no reason.
     */
    @Transactional
    public WebhookOutcome replay(UUID id, Consumer<PaymentWebhookEvent> handler) {
        PaymentWebhookEvent event = webhookEventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found"));
        if (event.getStatus() == WebhookEventStatus.PROCESSED) {
            throw new BadRequestException("This event already processed successfully — nothing to replay");
        }
        event.setAttemptCount(event.getAttemptCount() + 1);
        return runAndRecord(event, event.getEventType(), handler);
    }

    private WebhookOutcome runAndRecord(PaymentWebhookEvent event, String eventType, Consumer<PaymentWebhookEvent> handler) {
        try {
            handler.accept(event);
            event.setStatus("payment.captured".equals(eventType) ? WebhookEventStatus.PROCESSED : WebhookEventStatus.IGNORED);
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);
            return new WebhookOutcome(event, Outcome.PROCESSED);
        } catch (RuntimeException e) {
            log.error("Failed processing webhook event {} ({}), attempt {}: {}",
                    event.getRazorpayEventId(), eventType, event.getAttemptCount(), e.getMessage());
            event.setStatus(WebhookEventStatus.FAILED);
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);
            return new WebhookOutcome(event, Outcome.FAILED);
        }
    }

    @Transactional(readOnly = true)
    public Page<PaymentWebhookEventDto> list(WebhookEventStatus status, Pageable pageable) {
        Page<PaymentWebhookEvent> page = status != null
                ? webhookEventRepository.findByStatusOrderByReceivedAtDesc(status, pageable)
                : webhookEventRepository.findAllByOrderByReceivedAtDesc(pageable);
        return page.map(PaymentWebhookEventDto::from);
    }
}
