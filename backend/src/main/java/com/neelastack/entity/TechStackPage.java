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

/** A single programmatic-SEO silo landing page — see V17 migration for the rationale. */
@Entity
@Table(name = "tech_stack_pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechStackPage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    // Explicit column name is required here, not stylistic: Spring's default Hibernate
    // naming strategy only inserts an underscore at a lowercase->uppercase boundary, and
    // the digit '1' doesn't count as lowercase, so "h1Title" implicitly maps to "h1title"
    // (no underscore) rather than "h1_title" -- which is what the V17 migration actually
    // created. Leaving this implicit is exactly the mismatch that fails schema validation
    // on startup.
    @Column(name = "h1_title", nullable = false, length = 160)
    private String h1Title;

    @Column(nullable = false, length = 160)
    private String metaTitle;

    @Column(nullable = false, length = 300)
    private String metaDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String intro;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyContent;

    @Column(nullable = false, length = 200)
    private String primaryStack;

    @Column(length = 200)
    private String secondaryStack;

    @Column(length = 120)
    private String targetIndustry;

    /** Pipe-separated real use cases actually rendered on the page. */
    @Column(columnDefinition = "TEXT")
    private String useCases;

    @Column(length = 40)
    private String startingPrice;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private boolean published = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
