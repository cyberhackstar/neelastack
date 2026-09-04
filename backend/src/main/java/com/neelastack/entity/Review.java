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

@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue
    private UUID id;

    /** Nullable: a client-submitted testimonial can arrive before the engagement
     *  has a published case study to attach to (see TestimonialService#submit /
     *  V26 migration). An admin assigns this during moderation if left null. */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(name = "author_title", length = 160)
    private String authorTitle;

    /** Whole stars, 1–5 — enforced at the DB level too (CHECK constraint). */
    @Column(nullable = false)
    private Integer rating;

    @Column(name = "review_body", nullable = false, columnDefinition = "TEXT")
    private String reviewBody;

    @Builder.Default
    private boolean published = false;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Optional link to a client-recorded video testimonial (module 4). Never
     *  auto-populated -- set only when a real video URL was actually provided. */
    @Column(name = "video_url", length = 300)
    private String videoUrl;

    /** How this review arrived — the plain admin-authored CMS path, or the
     *  automated post-invoice testimonial-request flow. Purely provenance;
     *  doesn't affect publishing, which is always an explicit admin action. */
    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_via", nullable = false, length = 20)
    @Builder.Default
    private ReviewSource submittedVia = ReviewSource.ADMIN;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
