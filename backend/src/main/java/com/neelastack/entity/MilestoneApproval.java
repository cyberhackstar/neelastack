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

import java.time.LocalDateTime;
import java.util.UUID;

// Append-only, same as ProjectActivity: never updated once written, so no @EqualsAndHashCode
// concerns beyond identity. See Milestone/ProjectTask for why @Data is avoided (lazy
// @ManyToOne associations).
@Entity
@Table(name = "milestone_approval")
@Getter
@Setter
@ToString(exclude = {"milestone", "actor"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneApproval {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneApprovalAction action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // Always the client -- see MilestoneApprovalService, which restricts approve/requestChanges
    // to the engagement's own client -- but modeled as a plain User FK (not narrowed to Client)
    // for the same reason ProjectFile.uploadedBy and ProjectMessage.sender are: the actor may
    // later be deleted, and the association shouldn't need to change if that rule ever does.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
