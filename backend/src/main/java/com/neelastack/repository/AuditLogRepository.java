package com.neelastack.repository;

import com.neelastack.entity.AuditAction;
import com.neelastack.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only in practice: nothing in this codebase should call save() on an existing
 * AuditLog (only new inserts via AuditLogService.record), and the DB itself refuses
 * UPDATE/DELETE on audit_logs regardless (V22).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorEmail IS NULL OR a.actorEmail = :actorEmail)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(
            @Param("actorEmail") String actorEmail,
            @Param("action") AuditAction action,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
