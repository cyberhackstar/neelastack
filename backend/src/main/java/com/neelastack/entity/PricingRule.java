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
 * A database-backed pricing rule for one estimator service category (see
 * {@code EstimateCalculatorService#resolveServiceKey}). Replaces the hardcoded ranges
 * that previously lived directly in Java source — admin-editable via
 * {@code AdminPricingController} without a redeploy.
 *
 * {@code version}/{@code active} exist so a pricing change can be staged as a new row
 * and flipped on, keeping the previous version around for history, rather than
 * overwriting numbers in place with no trail.
 */
@Entity
@Table(name = "pricing_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "service_key", nullable = false, length = 60)
    private String serviceKey;

    @Column(name = "base_low", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseLow;

    /** Null means "no responsible automatic upper bound" — a scoped conversation instead. */
    @Column(name = "base_high", precision = 12, scale = 2)
    private BigDecimal baseHigh;

    @Builder.Default
    @Column(name = "complexity_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal complexityFactor = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "scale_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal scaleFactor = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "integration_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal integrationFactor = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "urgency_factor", nullable = false, precision = 6, scale = 3)
    private BigDecimal urgencyFactor = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @Column(length = 300)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
