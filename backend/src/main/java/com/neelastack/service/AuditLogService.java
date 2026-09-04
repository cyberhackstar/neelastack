package com.neelastack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.AuditLog;
import com.neelastack.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * The single write path for {@code audit_logs}. Called explicitly from each real
 * high-risk mutation point (InquiryService, QuotationService, InvoiceService,
 * PricingRuleService, ProjectFileService, the MFA controller, TokenRevocationService
 * callers, webhook replay) rather than a blanket AOP interceptor over "any mutation" --
 * that tends to either miss action-specific metadata or become too noisy to be useful.
 * See AuditAction for the full list this covers.
 *
 * Deliberately its own REQUIRES_NEW transaction: an audit write should not be rolled
 * back just because the surrounding business transaction later fails for an unrelated
 * reason, and a failed audit write should not be allowed to silently swallow the
 * business outcome either -- callers should call this after the mutation they're
 * recording has actually succeeded (best-effort logging is done via #recordBestEffort
 * where a missing entry is preferable to failing the whole request over it).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, String entityId, Map<String, Object> metadata) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorEmail = (auth != null && auth.isAuthenticated()) ? auth.getName() : "system";
        String actorRole = extractRole(auth);

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .requestId(MDC.get("requestId"))
                .ip(resolveClientIp());

        if (metadata != null && !metadata.isEmpty()) {
            try {
                builder.metadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.warn("Failed to serialize audit metadata for {} on {}/{}: {}", action, entityType, entityId, e.getMessage());
            }
        }

        auditLogRepository.save(builder.build());
    }

    /** Same as #record, but a failure here is logged rather than propagated -- for call sites where the audit write itself must never break the underlying operation. */
    public void recordBestEffort(AuditAction action, String entityType, String entityId, Map<String, Object> metadata) {
        try {
            record(action, entityType, entityId, metadata);
        } catch (Exception e) {
            log.error("Audit log write failed for {} on {}/{} — proceeding without it: {}", action, entityType, entityId, e.getMessage());
        }
    }

    public org.springframework.data.domain.Page<com.neelastack.dto.audit.AuditLogDto> search(
            String actorEmail, AuditAction action, String entityType, String entityId,
            java.time.LocalDateTime from, java.time.LocalDateTime to,
            org.springframework.data.domain.Pageable pageable) {
        return auditLogRepository.search(actorEmail, action, entityType, entityId, from, to, pageable)
                .map(this::toDto);
    }

    private com.neelastack.dto.audit.AuditLogDto toDto(AuditLog a) {
        return com.neelastack.dto.audit.AuditLogDto.builder()
                .id(a.getId())
                .actorEmail(a.getActorEmail())
                .actorRole(a.getActorRole())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .requestId(a.getRequestId())
                .ip(a.getIp())
                .metadata(a.getMetadata())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private String extractRole(Authentication auth) {
        if (auth == null) return null;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse(null);
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
