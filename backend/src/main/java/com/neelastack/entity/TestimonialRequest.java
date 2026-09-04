package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A tokenized, one-time invitation for a client to leave a testimonial after their
 * final invoice on an engagement is confirmed PAID (Client Acquisition & High-Ticket
 * Conversion Engine, module 4). Queued automatically by {@code TestimonialService}
 * from {@code InvoiceService}'s PAID transitions; never created directly by an admin.
 *
 * The eventual review (if submitted) lands in the existing {@code reviews} table —
 * this table only tracks the invitation/consumption lifecycle, not review content.
 */
@Entity
@Table(name = "testimonial_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestimonialRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "engagement_id", nullable = false)
    private UUID engagementId;

    /** Which published case study (if any) the review should be attached to.
     *  Null means the engagement has no public case study yet — the request is
     *  still sent, and a submitted review is simply held unattached to a project
     *  until an admin links one (see {@code TestimonialService#submit}). */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "client_email", nullable = false, length = 180)
    private String clientEmail;

    @Column(name = "client_name", nullable = false, length = 120)
    private String clientName;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TestimonialRequestStatus status = TestimonialRequestStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "review_id")
    private UUID reviewId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
