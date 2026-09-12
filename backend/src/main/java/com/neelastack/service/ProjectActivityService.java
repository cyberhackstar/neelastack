package com.neelastack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.dto.engagement.ProjectActivityDto;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectActivity;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.ProjectActivityRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes to and reads from {@code project_activity} — the client-safe project timeline
 * described in Section 9 of the client-workspace review ("Internal Audit Log ≠ Client
 * Activity Timeline"). Unlike {@link AuditLogService}, which is the single write path for
 * every high-risk security mutation across the whole app, this only ever gets called from
 * the handful of places in the delivery workflow that represent real, narratable project
 * progress: a milestone completing, a file landing, an invoice getting paid.
 *
 * Every business call site goes through {@link #recordBestEffort} rather than {@link #record}
 * directly — a purely informational feed must never be able to fail an upload, a status
 * change, or (especially) a payment confirmation. See AuditLogService#recordBestEffort for
 * the same reasoning applied to the internal audit trail.
 *
 * Deliberately depends on {@link EngagementRepository} rather than {@link EngagementService}:
 * EngagementService itself needs to call into this service to log ENGAGEMENT_CREATED /
 * ENGAGEMENT_STATUS_CHANGED, so a dependency back on EngagementService here would form a
 * circular bean graph (EngagementService -> ProjectActivityService -> EngagementService).
 * The access check in {@link #list} intentionally mirrors
 * EngagementService#getEntityWithAccessCheck for that reason rather than reusing it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectActivityService {

    private final ProjectActivityRepository projectActivityRepository;
    private final EngagementRepository engagementRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID engagementId, User actor, ProjectActivityType type, String summary, Map<String, Object> metadata) {
        // A reference proxy is all that's needed to populate the FK on insert -- this avoids
        // an extra SELECT on every single call site that already knows the id but may not
        // have a loaded Engagement entity in hand (e.g. InvoiceService's webhook path).
        Engagement engagementRef = engagementRepository.getReferenceById(engagementId);

        ProjectActivity.ProjectActivityBuilder builder = ProjectActivity.builder()
                .engagement(engagementRef)
                .actor(actor)
                // Snapshotted at write time, same rationale as AuditLog.actorEmail/actorRole:
                // the feed reads correctly even if the user is later renamed or removed.
                // Null actor (e.g. a webhook-confirmed payment) reads as "Neelastack" rather
                // than a blank name in the timeline.
                .actorName(actor != null ? actor.getFullName() : "Neelastack")
                .actorRole(actor != null ? actor.getRole().name() : null)
                .activityType(type)
                .summary(summary);

        if (metadata != null && !metadata.isEmpty()) {
            try {
                builder.metadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.warn("Failed to serialize activity metadata for {} on engagement {}: {}",
                        type, engagementId, e.getMessage());
            }
        }

        projectActivityRepository.save(builder.build());
    }

    /** Same as {@link #record}, but a failure here is logged rather than propagated. */
    public void recordBestEffort(UUID engagementId, User actor, ProjectActivityType type, String summary, Map<String, Object> metadata) {
        try {
            record(engagementId, actor, type, summary, metadata);
        } catch (Exception e) {
            log.error("Activity timeline write failed for {} on engagement {} — proceeding without it: {}",
                    type, engagementId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectActivityDto> list(UUID engagementId) {
        Engagement engagement = engagementRepository.findById(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement not found: " + engagementId));

        User current = currentUserProvider.get();
        boolean isOwner = engagement.getClient().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ADMIN || current.getRole() == Role.SUPERADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have access to this engagement");
        }

        return projectActivityRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId)
                .stream().map(this::toDto).toList();
    }

    private ProjectActivityDto toDto(ProjectActivity a) {
        return ProjectActivityDto.builder()
                .id(a.getId())
                .engagementId(a.getEngagement().getId())
                .actorName(a.getActorName())
                .actorRole(a.getActorRole())
                .activityType(a.getActivityType())
                .summary(a.getSummary())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
