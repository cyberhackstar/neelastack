package com.neelastack.service;

import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.MilestoneRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final EngagementService engagementService;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectActivityService projectActivityService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<MilestoneDto> list(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return milestoneRepository.findByEngagementIdOrderByDisplayOrderAsc(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public MilestoneDto create(UUID engagementId, MilestoneRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);

        Milestone milestone = Milestone.builder()
                .engagement(engagement)
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .status(MilestoneStatus.PENDING)
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();

        MilestoneDto dto = toDto(milestoneRepository.save(milestone));

        projectActivityService.recordBestEffort(engagementId, currentUserProvider.get(),
                ProjectActivityType.MILESTONE_CREATED, "Added milestone \"" + request.title() + "\"", null);

        return dto;
    }

    @Transactional
    public MilestoneDto updateStatus(UUID milestoneId, MilestoneStatus status) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));

        // Keep the service-layer invariant even if this method is called outside the current
        // admin controller. This also initializes the lazy engagement safely inside the tx.
        UUID engagementId = milestone.getEngagement().getId();
        engagementService.getEntityWithAccessCheck(engagementId);

        milestone.setStatus(status);
        MilestoneDto dto = toDto(milestoneRepository.save(milestone));

        String summary = status == MilestoneStatus.DONE
                ? "Milestone \"" + milestone.getTitle() + "\" marked complete"
                : "Milestone \"" + milestone.getTitle() + "\" status changed to " + status.name().replace('_', ' ');
        projectActivityService.recordBestEffort(engagementId, currentUserProvider.get(),
                ProjectActivityType.MILESTONE_STATUS_CHANGED, summary, null);

        if (status == MilestoneStatus.AWAITING_APPROVAL) {
            Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);
            notificationService.notifyBestEffort(engagement.getClient(), engagement,
                    com.neelastack.entity.NotificationType.MILESTONE_READY_FOR_APPROVAL,
                    "Milestone ready for your review — " + milestone.getTitle(),
                    "\"" + milestone.getTitle() + "\" is ready for approval. Please review it in your project workspace.",
                    "/dashboard/" + engagementId + "?tab=milestones");
        }

        return dto;
    }

    private MilestoneDto toDto(Milestone m) {
        return MilestoneDto.builder()
                .id(m.getId())
                .engagementId(m.getEngagement().getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .status(m.getStatus())
                .dueDate(m.getDueDate())
                .displayOrder(m.getDisplayOrder())
                .build();
    }
}
