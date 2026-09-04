package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records that an admin has deferred a follow-up task for a quotation -- either marked
 * done ({@code dismissedUntil == null}) or snoozed until a specific time. Purely a
 * record of the human's decision; it never shadows or mutates {@link Quotation} state.
 * One row per quotation (see {@code idx_follow_up_dismissals_quotation}, V20) -- a new
 * dismiss/snooze call replaces the previous deferral rather than accumulating history.
 */
@Entity
@Table(name = "follow_up_dismissals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpDismissal {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    @Column(name = "dismissed_by", nullable = false)
    private String dismissedBy;

    /** Null = dismissed indefinitely ("done"). Non-null = snoozed until this instant. */
    @Column(name = "dismissed_until")
    private LocalDateTime dismissedUntil;

    @Column(name = "reason", length = 300)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
