package com.neelastack.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A recurring weekly availability window, e.g. Monday 10:00-13:00. Multiple rows for
 * the same day_of_week model multiple windows (10:00-13:00 and 15:00-18:00) -- the
 * gap between them is the lunch/break, with no separate "breaks" table needed.
 * See master prompt section 2.
 */
@Entity
@Table(name = "availability_windows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityWindow {

    @Id
    @GeneratedValue
    private UUID id;

    /** ISO-8601: 1=Monday .. 7=Sunday, matching {@link java.time.DayOfWeek#getValue()}. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}



