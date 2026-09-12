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

// `recipient` and `engagement` are lazy @ManyToOne associations -- same @Data pitfall
// documented on Engagement/Milestone/Invoice in this package, so it's avoided here too.
@Entity
@Table(name = "notifications")
@Getter
@Setter
@ToString(exclude = {"recipient", "engagement"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** Nullable: a handful of notification types (none yet, but the column exists for
     *  future non-project events) aren't scoped to a single engagement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engagement_id")
    private Engagement engagement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "related_entity_type", length = 40)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    /** Frontend route the bell click should navigate to, e.g. "/dashboard/{engagementId}". */
    @Column(name = "deep_link", length = 300)
    private String deepLink;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
