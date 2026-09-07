package com.neelastack.repository;

import com.neelastack.entity.TestimonialRequest;
import com.neelastack.entity.TestimonialRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TestimonialRequestRepository extends JpaRepository<TestimonialRequest, UUID> {

    boolean existsByInvoiceId(UUID invoiceId);

    Optional<TestimonialRequest> findByToken(String token);

    /**
     * Atomic, one-time consumption of a testimonial link. Deliberately a
     * conditional UPDATE (not a read-then-write) so a resubmitted or replayed
     * request can never flip PENDING -> SUBMITTED twice, mirroring the pattern
     * this codebase already uses for one-time email-verification and MFA
     * recovery-code tokens. Returns the number of rows updated: 1 means this
     * caller won the race and should proceed to create the review; 0 means
     * someone else already consumed it (or it was never PENDING).
     */
    @Modifying
    @Query("""
            UPDATE TestimonialRequest t
            SET t.status = :status, t.respondedAt = :respondedAt, t.reviewId = :reviewId
            WHERE t.token = :token AND t.status = com.neelastack.entity.TestimonialRequestStatus.PENDING
            """)
    int consumeIfPending(@Param("token") String token,
                          @Param("status") TestimonialRequestStatus status,
                          @Param("respondedAt") LocalDateTime respondedAt,
                          @Param("reviewId") UUID reviewId);

    /**
     * Outbox-style retry candidates: still PENDING, the invite email hasn't been confirmed
     * sent yet, this row hasn't already exhausted its retry budget, and its backoff window has
     * elapsed. Bounded with a fetch limit via Pageable at the call site so one scheduler tick
     * can't pull an unbounded backlog into memory at once.
     */
    @Query("""
            SELECT t FROM TestimonialRequest t
            WHERE t.status = com.neelastack.entity.TestimonialRequestStatus.PENDING
              AND t.emailSentAt IS NULL
              AND t.emailAttempts < :maxAttempts
              AND t.nextEmailAttemptAt <= :cutoff
            ORDER BY t.nextEmailAttemptAt ASC
            """)
    java.util.List<TestimonialRequest> findRetryCandidates(@Param("maxAttempts") int maxAttempts,
                                                             @Param("cutoff") LocalDateTime cutoff,
                                                             org.springframework.data.domain.Pageable pageable);
}
