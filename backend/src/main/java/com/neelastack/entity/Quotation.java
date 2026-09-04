package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quotation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    @Column(nullable = false, length = 160)
    private String title;

    /**
     * Service-line key (e.g. "full-stack-migration") used to look up a matching
     * case study to show on the public proposal view — see QuotationService
     * #resolveCaseStudy. Set explicitly by the admin on QuotationRequest, or
     * left null and auto-inferred (best-effort keyword match against the source
     * inquiry's free-text projectType) at creation time. Null is a normal,
     * common state; it just means no case study is injected.
     */
    @Column(name = "service_category", length = 60)
    private String serviceCategory;

    @Column(columnDefinition = "TEXT")
    private String scopeSummary;

    @ElementCollection
    @CollectionTable(name = "quotation_line_items", joinColumns = @JoinColumn(name = "quotation_id"))
    @Builder.Default
    private List<QuotationLineItem> lineItems = List.of();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuotationStatus status = QuotationStatus.DRAFT;

    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, unique = true, length = 36)
    @Builder.Default
    private String publicToken = UUID.randomUUID().toString();

    @Column(length = 1000)
    private String responseReason;

    private LocalDateTime respondedAt;

    private LocalDateTime sentAt;

    // --- Proposal analytics (V15) — incremented on each public-token view. ---

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    private LocalDateTime lastViewedAt;

    // --- Proposal lifecycle analytics (V19) ---

    /** Set once, on the first public-token view. Never overwritten after that. */
    private LocalDateTime firstViewedAt;

    /** Set only on a SENT -> ACCEPTED transition; query-friendly split of respondedAt. */
    private LocalDateTime acceptedAt;

    /** Set only on a SENT -> REJECTED transition; query-friendly split of respondedAt. */
    private LocalDateTime rejectedAt;

    // --- Pricing version traceability (V21) ---

    /**
     * Which PricingRule (if any) an admin referenced when building this quotation's line
     * items. Nullable: quotations are created manually (QuotationService#create takes
     * explicit line items, unlike the public estimator which always resolves an active
     * rule), so there's no automatic link the way EstimateCalculatorService has -- an
     * admin opts in by passing pricingRuleId on QuotationRequest. Null legitimately means
     * "priced without reference to a configured rule" (fully custom/negotiated scope),
     * not missing data.
     */
    @Column(name = "pricing_rule_id")
    private UUID pricingRuleId;

    /**
     * Snapshot of PricingRule.version at the moment this quotation was created -- stored
     * separately from pricingRuleId because the rule row itself can be revised later
     * (new version, same id's lineage via serviceKey), and this quotation should keep
     * recording what was actually true when it was priced, not what the rule says today.
     */
    @Column(name = "pricing_rule_version")
    private Integer pricingRuleVersion;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
