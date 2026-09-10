package com.neelastack.repository;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.entity.LeadTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    /** Used by the booking-engine sales funnel (BookingAnalyticsService) — counts inquiries
     *  received in a date range without loading them all into memory. */
    long countByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    long countByCreatedAtBetweenAndLeadTierNot(java.time.LocalDateTime from, java.time.LocalDateTime to,
                                                com.neelastack.entity.LeadTier tier);
    Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);
    Page<Inquiry> findAll(Pageable pageable);
    long countByStatus(InquiryStatus status);
    long countByLeadTier(LeadTier leadTier);
}
