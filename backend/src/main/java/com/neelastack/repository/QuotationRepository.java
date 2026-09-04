package com.neelastack.repository;

import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
