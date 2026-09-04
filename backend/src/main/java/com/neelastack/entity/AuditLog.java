package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single high-risk-mutation audit entry. Append-only: the DB migration (V22) revokes
 * UPDATE/DELETE on this table and adds a raising trigger as a backstop, so this holds
 * even if application-layer authorization is ever misconfigured. Written only through
 * AuditLogService -- never edited or deleted through any code path, including this
 * entity's own setters (Hibernate needs @Data for the initial insert, but nothing in
 * this codebase should ever call save() on an already-persisted AuditLog).
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** Null for actions with no authenticated actor (shouldn't normally happen for admin-only actions). */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", nullable = false, length = 180)
    private String actorEmail;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(name = "request_id", length = 60)
    private String requestId;

    @Column(length = 64)
    private String ip;

    /**
     * Before/after or other context specific to the action type, serialized to JSON by
     * AuditLogService (Jackson ObjectMapper) before being set here. Stored as native
     * jsonb via Hibernate 6's built-in JSON mapping (@JdbcTypeCode) -- no extra
     * dependency needed beyond what's already in this project's Hibernate version.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
