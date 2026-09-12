package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Same reasoning as ProjectMessage/ProjectTask: `engagement`, `requestedBy`, and `attachment`
// are all lazy @ManyToOne, so @Data (and its generated toString/equals/hashCode) is avoided.
@Entity
@Table(name = "change_request")
@Getter
@Setter
@ToString(exclude = {"engagement", "requestedBy", "attachment"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequest {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ChangeRequestPriority priority = ChangeRequestPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChangeRequestStatus status = ChangeRequestStatus.SUBMITTED;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "estimated_cost_currency", length = 3)
    @Builder.Default
    private String estimatedCostCurrency = "INR";

    private Integer estimatedTimelineDays;

    // Reuses the existing ProjectFile upload pipeline, same pattern as ProjectMessage#attachment
    // -- e.g. a screenshot of the requested UI change (Section 14's "whatsapp-flow.png").
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_file_id")
    private ProjectFile attachment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
