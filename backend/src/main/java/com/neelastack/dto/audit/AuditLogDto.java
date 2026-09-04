package com.neelastack.dto.audit;

import com.neelastack.entity.AuditAction;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AuditLogDto(
        UUID id,
        String actorEmail,
        String actorRole,
        AuditAction action,
        String entityType,
        String entityId,
        String requestId,
        String ip,
        String metadata,
        LocalDateTime createdAt
) {}
