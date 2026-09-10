package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single scheduled appointment. Timestamps are stored as {@code timestamptz}
 * (effectively UTC) and converted to the client's/admin's timezone only at display
 * time -- see AvailabilityService. Double-booking is prevented at the database
 * level by an exclusion constraint (V33: excl_bookings_no_overlap), not solely by
 * application logic. See master prompt sections 15-21, 27-34, 40-46.
 */
@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 30)
    private String bookingNumber;

    @Column(name = "meeting_type_id", nullable = false)
    private UUID meetingTypeId;

    @Column(name = "inquiry_id")
    private UUID inquiryId;

    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    @Column(name = "client_name", nullable = false, length = 160)
    private String clientName;

    @Column(name = "client_email", nullable = false, length = 180)
    private String clientEmail;

    @Column(name = "client_phone", length = 30)
    private String clientPhone;

    @Column(name = "client_company", length = 160)
    private String clientCompany;

    @Column(name = "client_timezone", nullable = false, length = 60)
    @Builder.Default
    private String clientTimezone = "Asia/Kolkata";

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BookingOutcome outcome;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "no_show", nullable = false)
    @Builder.Default
    private Boolean noShow = false;

    @Column(name = "view_token", nullable = false, unique = true, length = 64)
    private String viewToken;

    @Column(name = "reschedule_token", nullable = false, unique = true, length = 64)
    private String rescheduleToken;

    @Column(name = "cancel_token", nullable = false, unique = true, length = 64)
    private String cancelToken;

    @Column(name = "idempotency_key", unique = true, length = 80)
    private String idempotencyKey;

    @Column(name = "requires_payment", nullable = false)
    @Builder.Default
    private Boolean requiresPayment = false;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 8)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private BookingPaymentStatus paymentStatus = BookingPaymentStatus.NOT_REQUIRED;

    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Column(length = 60)
    private String source;

    @Column(name = "utm_source", length = 120)
    private String utmSource;

    @Column(name = "utm_medium", length = 120)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 120)
    private String utmCampaign;

    @Column(name = "landing_page", length = 300)
    private String landingPage;

    @Column(length = 300)
    private String referrer;

    @Column(name = "calendar_event_id", length = 200)
    private String calendarEventId;

    @Column(name = "meeting_url", length = 300)
    private String meetingUrl;

    @Column(name = "confirmation_sent_at")
    private LocalDateTime confirmationSentAt;

    @Column(name = "reminder_24h_sent_at")
    private LocalDateTime reminder24hSentAt;

    @Column(name = "reminder_1h_sent_at")
    private LocalDateTime reminder1hSentAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
