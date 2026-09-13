package com.neelastack.repository;

import com.neelastack.entity.UpiPaymentSubmission;
import com.neelastack.entity.UpiSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UpiPaymentSubmissionRepository extends JpaRepository<UpiPaymentSubmission, UUID> {
    List<UpiPaymentSubmission> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
    @org.springframework.data.jpa.repository.Query("SELECT s FROM UpiPaymentSubmission s " +
            "JOIN FETCH s.invoice i JOIN FETCH s.upiMethod m " +
            "WHERE s.status = :status ORDER BY s.createdAt ASC")
    List<UpiPaymentSubmission> findByStatusWithInvoiceAndMethodOrderByCreatedAtAsc(UpiSubmissionStatus status);

    List<UpiPaymentSubmission> findByStatusOrderByCreatedAtAsc(UpiSubmissionStatus status);
    boolean existsByInvoiceIdAndStatus(UUID invoiceId, UpiSubmissionStatus status);

    long countByStatus(UpiSubmissionStatus status);
}
