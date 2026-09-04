package com.neelastack.repository;

import com.neelastack.entity.PaymentWebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Page<PaymentWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<PaymentWebhookEvent> findByStatusOrderByReceivedAtDesc(PaymentWebhookEvent.WebhookEventStatus status, Pageable pageable);

    Optional<PaymentWebhookEvent> findByRazorpayEventId(String razorpayEventId);

    /**
     * Row-locking lookups (SELECT ... FOR UPDATE) used by PaymentWebhookEventService so that
     * two near-simultaneous deliveries of the same event — a genuine Razorpay retry racing a
     * slow first attempt, or a concurrent admin replay — can't both decide the event is
     * claimable and process it twice. Callers hold the lock for the whole claim+process+mark
     * sequence (one @Transactional method), not just the initial lookup, so a second claimant
     * blocks until the first one has actually finished and committed its final status —
     * otherwise a lock released right after the claim step would leave the same window open
     * that this is meant to close.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PaymentWebhookEvent e where e.razorpayEventId = :eventId")
    Optional<PaymentWebhookEvent> findByRazorpayEventIdForUpdate(@Param("eventId") String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PaymentWebhookEvent e where e.id = :id")
    Optional<PaymentWebhookEvent> findByIdForUpdate(@Param("id") UUID id);
}
