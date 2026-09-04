package com.neelastack.service;

import com.neelastack.entity.PaymentWebhookEvent;
import com.neelastack.entity.PaymentWebhookEvent.WebhookEventStatus;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.PaymentWebhookEventRepository;
import com.neelastack.service.PaymentWebhookEventService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the idempotency + retry contract the review specifically asked for (items #2/#3):
 * a Razorpay webhook event id must be recorded exactly once, a genuinely duplicate delivery
 * (already PROCESSED) must be skipped without running the handler, but a FAILED — or
 * crashed-mid-processing — event must be reclaimable and reprocessed rather than being
 * permanently skipped as a duplicate.
 */
class PaymentWebhookEventServiceTest {

    private PaymentWebhookEventRepository repository;
    private PaymentWebhookEventService service;

    private static final Consumer<PaymentWebhookEvent> NOOP = e -> {};

    @BeforeEach
    void setUp() {
        repository = mock(PaymentWebhookEventRepository.class);
        service = new PaymentWebhookEventService(repository);
        // save()/saveAndFlush() just hand back whatever was passed to them, like a real
        // repository would after an UPDATE/INSERT — tests assert on the entity's own state.
        when(repository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PaymentWebhookEvent existingEvent(WebhookEventStatus status, int attempts) {
        return PaymentWebhookEvent.builder()
                .id(UUID.randomUUID())
                .razorpayEventId("evt_1")
                .eventType("payment.captured")
                .razorpayOrderId("order_1")
                .rawPayload("{}")
                .status(status)
                .attemptCount(attempts)
                .build();
    }

    // --- handleDelivery ---

    @Test
    void handleDelivery_firstDelivery_runsHandlerAndMarksProcessed() {
        when(repository.findByRazorpayEventIdForUpdate("evt_1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.handleDelivery("evt_1", "payment.captured", "pay_1", "order_1", "{}", NOOP);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(result.event().getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(result.event().getProcessedAt()).isNotNull();
    }

    @Test
    void handleDelivery_unhandledEventType_marksIgnoredNotProcessed() {
        when(repository.findByRazorpayEventIdForUpdate("evt_2")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.handleDelivery("evt_2", "order.paid", null, "order_1", "{}", NOOP);

        assertThat(result.event().getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
    }

    @Test
    void handleDelivery_handlerThrows_marksFailedButDoesNotPropagate() {
        when(repository.findByRazorpayEventIdForUpdate("evt_1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentWebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Consumer<PaymentWebhookEvent> failingHandler = e -> { throw new RuntimeException("db hiccup"); };

        var result = service.handleDelivery("evt_1", "payment.captured", "pay_1", "order_1", "{}", failingHandler);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.event().getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(result.event().getProcessedAt()).isNotNull();
    }

    @Test
    void handleDelivery_alreadyProcessed_isDuplicateAndHandlerNeverRuns() {
        when(repository.findByRazorpayEventIdForUpdate("evt_1")).thenReturn(Optional.of(existingEvent(WebhookEventStatus.PROCESSED, 1)));
        @SuppressWarnings("unchecked")
        Consumer<PaymentWebhookEvent> handler = mock(Consumer.class);

        var result = service.handleDelivery("evt_1", "payment.captured", "pay_1", "order_1", "{}", handler);

        assertThat(result.outcome()).isEqualTo(Outcome.DUPLICATE);
        verifyNoInteractions(handler);
        verify(repository, never()).save(any());
    }

    @Test
    void handleDelivery_previouslyFailed_isReclaimedAndCanSucceedOnRetry() {
        // This is the exact gap the review flagged: a FAILED event must NOT be treated as a
        // permanent duplicate — it has to be reclaimable so a retried delivery can actually
        // succeed the second time (e.g. after the DB hiccup that failed it the first time
        // is over).
        when(repository.findByRazorpayEventIdForUpdate("evt_1")).thenReturn(Optional.of(existingEvent(WebhookEventStatus.FAILED, 1)));

        var result = service.handleDelivery("evt_1", "payment.captured", "pay_1", "order_1", "{}", NOOP);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(result.event().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void handleDelivery_stuckReceived_isReclaimedForReprocessing() {
        // Crash mid-processing (never reached the terminal status) leaves a RECEIVED row
        // behind — must be reclaimable exactly like FAILED, not treated as "already handled".
        when(repository.findByRazorpayEventIdForUpdate("evt_1")).thenReturn(Optional.of(existingEvent(WebhookEventStatus.RECEIVED, 1)));

        var result = service.handleDelivery("evt_1", "payment.captured", "pay_1", "order_1", "{}", NOOP);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(result.event().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void handleDelivery_concurrentFirstDelivery_losesRaceAndDoesNotRunHandler() {
        // findByRazorpayEventIdForUpdate raced a concurrent first-time delivery of the same
        // event and missed it, but the unique constraint on razorpay_event_id catches it at
        // insert time. The loser must not run the handler (nor throw) — the winner owns it.
        when(repository.findByRazorpayEventIdForUpdate("evt_race")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(PaymentWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(repository.findByRazorpayEventId("evt_race"))
                .thenReturn(Optional.of(existingEvent(WebhookEventStatus.RECEIVED, 1)));
        @SuppressWarnings("unchecked")
        Consumer<PaymentWebhookEvent> handler = mock(Consumer.class);

        var result = service.handleDelivery("evt_race", "payment.captured", "pay_1", "order_1", "{}", handler);

        assertThat(result.outcome()).isEqualTo(Outcome.DUPLICATE);
        verifyNoInteractions(handler);
    }

    // --- replay (admin manual replay, review item #3) ---

    @Test
    void replay_failedEvent_reArmsAndReprocesses() {
        var event = existingEvent(WebhookEventStatus.FAILED, 2);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        var result = service.replay(event.getId(), NOOP);

        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(result.event().getAttemptCount()).isEqualTo(3);
    }

    @Test
    void replay_handlerFailsAgain_marksFailedAgain() {
        var event = existingEvent(WebhookEventStatus.FAILED, 1);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        Consumer<PaymentWebhookEvent> failingHandler = e -> { throw new RuntimeException("still broken"); };

        var result = service.replay(event.getId(), failingHandler);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void replay_alreadyProcessed_refusesWithoutRunningHandler() {
        var event = existingEvent(WebhookEventStatus.PROCESSED, 1);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        @SuppressWarnings("unchecked")
        Consumer<PaymentWebhookEvent> handler = mock(Consumer.class);

        assertThatThrownBy(() -> service.replay(event.getId(), handler))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void replay_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay(id, NOOP))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
