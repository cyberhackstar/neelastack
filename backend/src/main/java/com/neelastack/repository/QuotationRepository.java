package com.neelastack.repository;

import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    List<Quotation> findByInquiryIdOrderByCreatedAtDesc(UUID inquiryId);
    Optional<Quotation> findByPublicToken(String publicToken);
    List<Quotation> findByStatusAndValidUntilBefore(QuotationStatus status, LocalDate cutoff);
    List<Quotation> findByStatus(QuotationStatus status);

    /**
     * Atomic, one-time accept/reject of a SENT quotation. Deliberately a conditional UPDATE
     * (not read-then-write) so two concurrent responses on the same public link -- a double
     * click, a client retry racing itself, two browser tabs -- can never both succeed with
     * different outcomes (one ACCEPTED, one REJECTED) or both fire the response-notice email.
     * Mirrors TestimonialRequestRepository#consumeIfPending. Returns the number of rows
     * updated: 1 means this caller won the race and should proceed; 0 means the quotation
     * was no longer SENT by the time this ran (already responded to, or expired concurrently).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Quotation q
            SET q.status = :status, q.responseReason = :reason, q.respondedAt = :respondedAt,
                q.acceptedAt = :acceptedAt, q.rejectedAt = :rejectedAt
            WHERE q.publicToken = :token AND q.status = com.neelastack.entity.QuotationStatus.SENT
            """)
    int respondIfSent(@Param("token") String token,
                       @Param("status") QuotationStatus status,
                       @Param("reason") String reason,
                       @Param("respondedAt") LocalDateTime respondedAt,
                       @Param("acceptedAt") LocalDateTime acceptedAt,
                       @Param("rejectedAt") LocalDateTime rejectedAt);

    /**
     * Sent but never opened, and stale enough to nudge — the "3-day reminder for
     * unviewed proposals" case. viewCount is used (not firstViewedAt) so it also
     * catches any legacy row where firstViewedAt wasn't backfilled.
     */
    List<Quotation> findByStatusAndViewCountAndSentAtBefore(
            QuotationStatus status, Integer viewCount, LocalDateTime cutoff);

    /**
     * Opened at least once but still sitting unanswered, and stale enough to escalate —
     * the "priority alert for viewed-but-unanswered" case. Ordered so the longest-silent,
     * most-viewed proposals surface first.
     */
    List<Quotation> findByStatusAndViewCountGreaterThanAndLastViewedAtBeforeOrderByLastViewedAtAsc(
            QuotationStatus status, Integer viewCount, LocalDateTime cutoff);

    List<Quotation> findByStatusAndSentAtIsNotNull(QuotationStatus status);
}
