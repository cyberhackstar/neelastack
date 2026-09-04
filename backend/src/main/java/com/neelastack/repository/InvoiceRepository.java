package com.neelastack.repository;

import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByEngagementIdOrderByCreatedAtDesc(UUID engagementId);
    Optional<Invoice> findByRazorpayOrderId(String razorpayOrderId);
    long countByInvoiceNumberStartingWith(String prefix);

    /**
     * Row-locked read used by InvoiceService#createOrder to serialize concurrent checkout
     * requests against the *same* invoice: the second concurrent caller blocks on this query
     * until the first transaction commits, so both see a consistent view of "is there already
     * a live payment attempt" instead of racing past each other and creating two Razorpay
     * orders for one invoice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Same row-lock pattern as findByIdForUpdate, keyed by the Razorpay order id instead —
     * used by markPaidFromWebhook so the webhook path and the browser-verification path
     * (verifyAndConfirmPayment) can never race each other into an inconsistent invoice state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.razorpayOrderId = :razorpayOrderId")
    Optional<Invoice> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);

    /**
     * PENDING invoices with a Razorpay order already created, past a short grace window
     * (so we don't race a checkout the client is actively completing right now) — the
     * candidate set for PaymentReconciliationService's polling sweep.
     */
    List<Invoice> findByStatusAndRazorpayOrderIdIsNotNullAndCreatedAtBefore(
            InvoiceStatus status, LocalDateTime cutoff);

    /**
     * Atomically increments (or creates) the invoice-number counter for the given year and
     * returns the new value in one statement. The INSERT ... ON CONFLICT DO UPDATE is a single
     * row-level write in Postgres, so concurrent callers are serialized by the row lock the
     * UPDATE branch takes — no application-level retry loop is needed to avoid a duplicate
     * invoice number.
     *
     * Deliberately NOT annotated @Modifying: the statement has a RETURNING clause, so the
     * Postgres JDBC driver hands back a result set just like a SELECT would, and Spring Data
     * needs to call getSingleResult() (query semantics) rather than executeUpdate() to read it.
     */
    @Query(value = """
            INSERT INTO invoice_number_counters (year, last_value)
            VALUES (:year, 1)
            ON CONFLICT (year) DO UPDATE SET last_value = invoice_number_counters.last_value + 1
            RETURNING last_value
            """, nativeQuery = true)
    long nextInvoiceSequenceForYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = 'PAID'")
    BigDecimal sumPaidAmount();

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = 'PENDING'")
    BigDecimal sumPendingAmount();
}
