package com.neelastack.repository;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.entity.LeadTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {
    Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);
    Page<Inquiry> findAll(Pageable pageable);
    long countByStatus(InquiryStatus status);
    long countByLeadTier(LeadTier leadTier);
}
