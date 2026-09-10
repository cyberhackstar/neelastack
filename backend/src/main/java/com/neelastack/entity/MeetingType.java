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
import java.util.UUID;

/**
 * An admin-configured appointment template (e.g. "Discovery Call", "Project
 * Consultation") that a public booking page is generated for. See booking-engine
 * master prompt section 1.
 */
@Entity
@Table(name = "meeting_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingType {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 140)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "buffer_before_minutes", nullable = false)
    @Builder.Default
    private Integer bufferBeforeMinutes = 0;

    @Column(name = "buffer_after_minutes", nullable = false)
    @Builder.Default
    private Integer bufferAfterMinutes = 0;

    @Column(name = "min_notice_minutes", nullable = false)
    @Builder.Default
    private Integer minNoticeMinutes = 120;

    @Column(name = "max_horizon_days", nullable = false)
    @Builder.Default
    private Integer maxHorizonDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 20)
    @Builder.Default
    private LocationType locationType = LocationType.GOOGLE_MEET;

    @Column(name = "location_detail", length = 300)
    private String locationDetail;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 8, nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "requires_payment", nullable = false)
    @Builder.Default
    private Boolean requiresPayment = false;

    @Column(name = "cancellable_until_hours", nullable = false)
    @Builder.Default
    private Integer cancellableUntilHours = 12;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
