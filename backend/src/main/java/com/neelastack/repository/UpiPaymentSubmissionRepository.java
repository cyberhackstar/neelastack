package com.neelastack.repository;

import com.neelastack.entity.UpiPaymentSubmission;
import com.neelastack.entity.UpiSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UpiPaymentSubmissionRepository extends JpaRepository<UpiPaymentSubmission, UUID> {
    List<UpiPaymentSubmission> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    List<UpiPaymentSubmission> findByStatusOrderByCreatedAtAsc(UpiSubmissionStatus status);
    long countByStatus(UpiSubmissionStatus status);
}
