package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// See ProjectMessage/Milestone for why @Data is avoided here: `engagement` and `actor` are
// lazy @ManyToOne associations, and @Data's generated toString() would touch both.
// Append-only from the application's perspective (written once by ProjectActivityService,
// never updated) -- no @EqualsAndHashCode override is needed since rows are never compared
// or deduplicated in memory, unlike Project/Milestone/User elsewhere in this package.
@Entity
@Table(name = "project_activity")
@Getter
@Setter
@ToString(exclude = {"engagement", "actor"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectActivity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ProjectActivityType activityType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** Optional structured context (e.g. {"fileName": "...", "milestoneId": "..."}), serialized by ProjectActivityService. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
