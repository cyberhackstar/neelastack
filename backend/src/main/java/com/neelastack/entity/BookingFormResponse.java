package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A client's answer to one dynamic booking-form field, snapshotted at booking time. */
@Entity
@Table(name = "booking_form_responses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingFormResponse {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String value;
}
