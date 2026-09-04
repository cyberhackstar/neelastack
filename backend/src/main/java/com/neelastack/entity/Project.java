package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String problemStatement;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(length = 300)
    private String coverImageUrl;

    @ElementCollection
    @CollectionTable(name = "project_tech_stack", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "technology", length = 60)
    @Builder.Default
    private List<String> techStack = List.of();

    @Column(length = 300)
    private String liveUrl;

    @Column(length = 300)
    private String repoUrl;

    @Builder.Default
    private boolean featured = false;

    @Builder.Default
    private boolean published = true;

    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * Service-line keys (e.g. "full-stack-migration", "ecommerce", "saas-platform")
     * this case study can credibly back up — set by an admin, used only to pick a
     * relevant proof point for a matching quotation (see QuotationService /
     * PublicQuotationDto#relatedCaseStudy). Empty by default; a quotation whose
     * service category matches nothing here simply shows no case study, same
     * "real data or nothing" rule as {@code keyMetrics} below and the existing
     * review/rating fields on this entity.
     */
    @ElementCollection
    @CollectionTable(name = "project_service_categories", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "category", length = 60)
    @Builder.Default
    private List<String> serviceCategories = List.of();

    /**
     * Short, verified, admin-entered outcome statements (e.g. "Migrated a
     * single-tenant PHP app to a modular Spring Boot + Angular platform with zero
     * unplanned downtime"). Never generated, estimated, or fabricated — an admin
     * writes these only when they can stand behind them. Empty means "nothing
     * verified to show yet", which is the correct, honest default for a new case
     * study, not a gap to fill with a plausible-sounding placeholder.
     */
    @ElementCollection
    @CollectionTable(name = "project_key_metrics", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "metric", length = 200)
    @Builder.Default
    private List<String> keyMetrics = List.of();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
