package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A one-off override for a single calendar date: either a full-day block (holiday)
 * or a narrower special-hours window that replaces the normal weekly rule for that
 * date only. See master prompt section 2 ("Holidays" / "Special dates").
 */
@Entity
@Table(name = "availability_date_overrides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityDateOverride {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "override_date", nullable = false, unique = true)
    private LocalDate overrideDate;

    /** False = whole day blocked (holiday). True = only startTime-endTime is bookable. */
    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 200)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
