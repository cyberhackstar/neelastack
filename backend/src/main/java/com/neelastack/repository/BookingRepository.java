package com.neelastack.repository;

import com.neelastack.entity.Booking;
import com.neelastack.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByViewToken(String viewToken);
    Optional<Booking> findByCancelToken(String cancelToken);
    Optional<Booking> findByRescheduleToken(String rescheduleToken);
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    Optional<Booking> findByBookingNumber(String bookingNumber);

    /** Live (not cancelled/rescheduled-away) bookings overlapping a window, for a pre-check
     * before relying on the DB exclusion constraint as the final word. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status NOT IN ('CANCELLED', 'RESCHEDULED')
            AND b.startAt < :endAt AND b.endAt > :startAt
            """)
    List<Booking> findOverlapping(@Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status NOT IN ('CANCELLED', 'RESCHEDULED')
            AND b.startAt >= :from AND b.startAt < :to
            ORDER BY b.startAt ASC
            """)
    List<Booking> findLiveBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    Page<Booking> findByStatusOrderByStartAtDesc(BookingStatus status, Pageable pageable);
    Page<Booking> findAllByOrderByStartAtDesc(Pageable pageable);

    @Query("""
            SELECT b FROM Booking b
            WHERE (:status IS NULL OR b.status = :status)
            AND (:meetingTypeId IS NULL OR b.meetingTypeId = :meetingTypeId)
            AND (:search IS NULL OR LOWER(b.clientName) LIKE :search OR LOWER(b.clientEmail) LIKE :search OR LOWER(b.clientCompany) LIKE :search)
            ORDER BY b.startAt DESC
            """)
    Page<Booking> search(@Param("status") BookingStatus status,
                          @Param("meetingTypeId") UUID meetingTypeId,
                          @Param("search") String search,
                          Pageable pageable);

    long countByStatus(BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.status NOT IN ('CANCELLED', 'RESCHEDULED') AND b.startAt >= :from AND b.startAt < :to")
    long countLiveBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT COALESCE(SUM(b.price), 0) FROM Booking b WHERE b.paymentStatus = 'PAID' AND b.startAt >= :from AND b.startAt < :to")
    java.math.BigDecimal sumRevenueBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    long countByNoShowTrue();

    /** Bookings whose 24h reminder is due (starts in the next 24-25h window, not yet sent, still live). */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status IN ('SCHEDULED', 'CONFIRMED')
            AND b.reminder24hSentAt IS NULL
            AND b.startAt BETWEEN :from AND :to
            """)
    List<Booking> findDue24hReminders(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status IN ('SCHEDULED', 'CONFIRMED')
            AND b.reminder1hSentAt IS NULL
            AND b.startAt BETWEEN :from AND :to
            """)
    List<Booking> findDue1hReminders(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Meetings that ended a while ago and still have no completed/no-show/cancelled outcome status. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status IN ('SCHEDULED', 'CONFIRMED')
            AND b.endAt < :cutoff
            """)
    List<Booking> findPastUnresolved(@Param("cutoff") OffsetDateTime cutoff);

    /** Expired, never-paid payment holds -- safe to release back into the availability pool. */
    List<Booking> findByPaymentStatusAndHoldExpiresAtBefore(
            com.neelastack.entity.BookingPaymentStatus paymentStatus, OffsetDateTime cutoff);

    long countByInquiryIdIsNotNull();
}
