package com.neelastack.service;

import com.neelastack.dto.engagement.MilestoneApprovalDto;
import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneApproval;
import com.neelastack.entity.MilestoneApprovalAction;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.MilestoneApprovalRepository;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Section 13 of the client-workspace review: a deliverable moves to AWAITING_APPROVAL (via
 * the existing generic admin status-update endpoint -- see MilestoneStatus), and only the
 * engagement's own client can then either approve it or send it back with a comment. Unlike
 * every other mutation in the engagement/milestone/task family, this one is deliberately
 * NOT reachable from {@code /api/v1/admin/**} at all: the whole point is a client decision
 * that staff can see but not make on the client's behalf.
 */
@Service
@RequiredArgsConstructor
public class MilestoneApprovalService {

    private final MilestoneApprovalRepository milestoneApprovalRepository;
    private final MilestoneRepository milestoneRepository;
    private final EngagementService engagementService;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectActivityService projectActivityService;
    private final NotificationService notificationService;

    @Transactional
    public MilestoneDto approve(UUID milestoneId) {
        Milestone milestone = requireAwaitingApproval(milestoneId);
        Engagement engagement = requireClientOwner(milestone);
        User client = currentUserProvider.get();

        milestone.setStatus(MilestoneStatus.DONE);
        milestoneRepository.save(milestone);
        milestoneApprovalRepository.save(MilestoneApproval.builder()
                .milestone(milestone).action(MilestoneApprovalAction.APPROVED).actor(client).build());

        projectActivityService.recordBestEffort(engagement.getId(), client,
                ProjectActivityType.MILESTONE_APPROVED,
                "Approved milestone \"" + milestone.getTitle() + "\"", null);

        notificationService.notifyAllAdminsBestEffort(engagement,
                com.neelastack.entity.NotificationType.MILESTONE_APPROVED, com.neelastack.entity.NotificationPriority.LOW,
                "Milestone approved — " + milestone.getTitle(),
                client.getFullName() + " approved \"" + milestone.getTitle() + "\".",
                "/admin/engagements/" + engagement.getId());

        return toMilestoneDto(milestone);
    }

    @Transactional
    public MilestoneDto requestChanges(UUID milestoneId, String comment) {
        Milestone milestone = requireAwaitingApproval(milestoneId);
        Engagement engagement = requireClientOwner(milestone);
        User client = currentUserProvider.get();

        milestone.setStatus(MilestoneStatus.CHANGES_REQUESTED);
        milestoneRepository.save(milestone);
        milestoneApprovalRepository.save(MilestoneApproval.builder()
                .milestone(milestone).action(MilestoneApprovalAction.CHANGES_REQUESTED).comment(comment).actor(client).build());

        projectActivityService.recordBestEffort(engagement.getId(), client,
                ProjectActivityType.MILESTONE_CHANGES_REQUESTED,
                "Requested changes on milestone \"" + milestone.getTitle() + "\": \"" + truncate(comment) + "\"", null);

        notificationService.notifyAllAdminsBestEffort(engagement,
                com.neelastack.entity.NotificationType.MILESTONE_CHANGES_REQUESTED, com.neelastack.entity.NotificationPriority.HIGH,
                "Changes requested — " + milestone.getTitle(),
                client.getFullName() + " requested changes on \"" + milestone.getTitle() + "\": " + truncate(comment),
                "/admin/engagements/" + engagement.getId());

        return toMilestoneDto(milestone);
    }

    @Transactional(readOnly = true)
    public List<MilestoneApprovalDto> listForEngagement(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return milestoneApprovalRepository.findByEngagementId(engagementId).stream().map(this::toDto).toList();
    }

    private Milestone requireAwaitingApproval(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        if (milestone.getStatus() != MilestoneStatus.AWAITING_APPROVAL) {
            throw new BadRequestException("This milestone is not awaiting your approval right now");
        }
        return milestone;
    }

    /**
     * Deliberately does not accept a staff bypass: {@link EngagementService#getEntityWithAccessCheck}
     * is used everywhere else in this package precisely because staff also need read/write
     * access to engagement-scoped resources, but an approval decision is the one action in
     * this whole workspace that belongs to the client alone.
     */
    private Engagement requireClientOwner(Milestone milestone) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(milestone.getEngagement().getId());
        User current = currentUserProvider.get();
        boolean isOwningClient = current.getRole() == Role.CLIENT && engagement.getClient().getId().equals(current.getId());
        if (!isOwningClient) {
            throw new AccessDeniedException("Only the project's client can approve milestones or request changes");
        }
        return engagement;
    }

    private String truncate(String comment) {
        String trimmed = comment.trim();
        return trimmed.length() > 140 ? trimmed.substring(0, 140) + "…" : trimmed;
    }

    private MilestoneDto toMilestoneDto(Milestone m) {
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

    private MilestoneApprovalDto toDto(MilestoneApproval a) {
        return MilestoneApprovalDto.builder()
                .id(a.getId())
                .milestoneId(a.getMilestone().getId())
                .engagementId(a.getMilestone().getEngagement().getId())
                .action(a.getAction())
                .comment(a.getComment())
                .actorName(a.getActor().getFullName())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
