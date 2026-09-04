package com.neelastack.repository;

import com.neelastack.entity.PaymentAttempt;
import com.neelastack.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByRazorpayOrderId(String razorpayOrderId);

    List<PaymentAttempt> findByInvoiceIdAndStatus(UUID invoiceId, PaymentAttemptStatus status);

    /**
     * Marks every currently-CREATED attempt for an invoice as SUPERSEDED in one statement,
     * used right before recording a fresh attempt so exactly one CREATED row ever exists
     * per invoice at a time. Runs inside the same pessimistic-locked transaction as the
     * invoice row lock in InvoiceService#createOrder, so this is race-free by the same
     * row-lock ordering, not by anything special in this query itself.
     */
    @Modifying
    @Query("UPDATE PaymentAttempt p SET p.status = com.neelastack.entity.PaymentAttemptStatus.SUPERSEDED " +
            "WHERE p.invoice.id = :invoiceId AND p.status = com.neelastack.entity.PaymentAttemptStatus.CREATED")
    void supersedeLiveAttempts(@Param("invoiceId") UUID invoiceId);
}
