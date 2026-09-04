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

@Entity
@Table(name = "inquiries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inquiry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String company;

    @Column(length = 80)
    private String projectType;

    @Column(length = 60)
    private String budgetRange;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.NEW;

    @Column(length = 60)
    private String source;

    // --- Project-estimator intake (V14) — all nullable, so plain /contact submissions
    // that don't go through the estimator remain valid rows. ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryIntent intent = InquiryIntent.GENERAL;

    @Column(columnDefinition = "TEXT")
    private String existingSystem;

    @Column(columnDefinition = "TEXT")
    private String scopeDetails;

    @Column(length = 60)
    private String usersScale;

    @Column(columnDefinition = "TEXT")
    private String integrations;

    @Column(length = 60)
    private String timeline;

    @Column(length = 40)
    private String urgency;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimateLow;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimateHigh;

    @Column(length = 8, nullable = false)
    @Builder.Default
    private String estimateCurrency = "INR";

    // --- Lead scoring (V14) — computed at submission time, see LeadScoringService. ---

    @Column(nullable = false)
    @Builder.Default
    private Integer leadScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private LeadTier leadTier = LeadTier.NURTURE;

    // --- Marketing attribution (V14), per master prompt section 47 — captured client-side
    // from query params/referrer, not verified server-side. ---

    @Column(length = 120)
    private String utmSource;

    @Column(length = 120)
    private String utmMedium;

    @Column(length = 120)
    private String utmCampaign;

    @Column(length = 300)
    private String referrer;

    @Column(length = 300)
    private String landingPage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
