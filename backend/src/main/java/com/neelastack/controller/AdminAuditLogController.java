package com.neelastack.controller;

import com.neelastack.dto.audit.AuditLogDto;
import com.neelastack.entity.AuditAction;
import com.neelastack.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Read-only: ROLE_ADMIN can query audit_logs, but no endpoint anywhere lets it be
 * mutated -- see AuditLogService (append-only write path) and V22 (DB-level
 * REVOKE UPDATE/DELETE + trigger backstop).
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin — audit logs", description = "Read-only, immutable audit trail — requires ROLE_ADMIN")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Page<AuditLogDto> search(
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogService.search(actorEmail, action, entityType, entityId, from, to, pageable);
    }
}
