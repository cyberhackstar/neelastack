package com.neelastack.service;

import com.neelastack.dto.engagement.ChangeRequestCreateRequest;
import com.neelastack.dto.engagement.ChangeRequestDto;
import com.neelastack.dto.engagement.ChangeRequestQuoteRequest;
import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.entity.ChangeRequest;
import com.neelastack.entity.ChangeRequestPriority;
import com.neelastack.entity.ChangeRequestStatus;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.ProjectFile;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ChangeRequestRepository;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Section 14 of the client-workspace review: "turns scope creep into revenue" -- a client
 * submits a change request, staff attach a cost/timeline quote, and only the engagement's
 * own client can accept or decline it (same client-only pattern as
 * {@link MilestoneApprovalService}, for the same reason: the acceptance itself is the
 * client's commercial decision, not staff's to make on their behalf). {@link #complete} is
 * the one admin-only lifecycle step, since delivering the accepted work is staff's call.
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    private final ChangeRequestRepository changeRequestRepository;
    private final ProjectFileRepository projectFileRepository;
    private final EngagementService engagementService;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectActivityService projectActivityService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ChangeRequestDto> list(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return changeRequestRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ChangeRequestDto create(UUID engagementId, ChangeRequestCreateRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);
        User requester = currentUserProvider.get();

        ProjectFile attachment = null;
        if (request.attachmentFileId() != null) {
            attachment = projectFileRepository.findById(request.attachmentFileId())
                    .orElseThrow(() -> new ResourceNotFoundException("File not found: " + request.attachmentFileId()));
            // Same cross-engagement check as ProjectMessageService#send -- see that method's
            // comment for why the caller having access to *an* engagement isn't enough.
            if (!attachment.getEngagement().getId().equals(engagementId)) {
                throw new BadRequestException("That file does not belong to this project");
            }
        }

        ChangeRequest changeRequest = ChangeRequest.builder()
                .engagement(engagement)
                .requestedBy(requester)
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : ChangeRequestPriority.MEDIUM)
                .status(ChangeRequestStatus.SUBMITTED)
                .attachment(attachment)
                .build();

        ChangeRequestDto dto = toDto(changeRequestRepository.save(changeRequest));

        projectActivityService.recordBestEffort(engagementId, requester,
                ProjectActivityType.CHANGE_REQUEST_SUBMITTED, "Requested a change: \"" + request.title() + "\"", null);

        return dto;
    }

    @Transactional
    public ChangeRequestDto quote(UUID changeRequestId, ChangeRequestQuoteRequest request) {
        ChangeRequest changeRequest = getWithAccessCheck(changeRequestId);
        if (changeRequest.getStatus() != ChangeRequestStatus.SUBMITTED && changeRequest.getStatus() != ChangeRequestStatus.QUOTED) {
            throw new BadRequestException("Only a submitted (or already-quoted) change request can be quoted");
        }

        changeRequest.setEstimatedCost(request.estimatedCost());
        changeRequest.setEstimatedCostCurrency(request.estimatedCostCurrency() != null ? request.estimatedCostCurrency() : "INR");
        changeRequest.setEstimatedTimelineDays(request.estimatedTimelineDays());
        changeRequest.setStatus(ChangeRequestStatus.QUOTED);
        ChangeRequestDto dto = toDto(changeRequestRepository.save(changeRequest));

        String impact = "+" + changeRequest.getEstimatedCostCurrency() + " " + request.estimatedCost()
                + (request.estimatedTimelineDays() != null ? ", +" + request.estimatedTimelineDays() + " days" : "");
        projectActivityService.recordBestEffort(changeRequest.getEngagement().getId(), currentUserProvider.get(),
                ProjectActivityType.CHANGE_REQUEST_QUOTED,
                "Quoted change request \"" + changeRequest.getTitle() + "\" (" + impact + ")", null);

        Engagement engagement = changeRequest.getEngagement();
        notificationService.notifyBestEffort(engagement.getClient(), engagement,
                com.neelastack.entity.NotificationType.CHANGE_REQUEST_QUOTED,
                "Quote ready — " + changeRequest.getTitle(),
                "Your change request \"" + changeRequest.getTitle() + "\" has been quoted: " + impact + ". Please review and accept or decline.",
                "/dashboard/" + engagement.getId() + "?tab=change-requests");

        return dto;
    }

    @Transactional
    public ChangeRequestDto accept(UUID changeRequestId) {
        ChangeRequest changeRequest = requireOwningClientAndQuoted(changeRequestId);
        changeRequest.setStatus(ChangeRequestStatus.ACCEPTED);
        ChangeRequestDto dto = toDto(changeRequestRepository.save(changeRequest));

        projectActivityService.recordBestEffort(changeRequest.getEngagement().getId(), currentUserProvider.get(),
                ProjectActivityType.CHANGE_REQUEST_ACCEPTED,
                "Accepted the quote for \"" + changeRequest.getTitle() + "\"", null);

        return dto;
    }

    @Transactional
    public ChangeRequestDto decline(UUID changeRequestId) {
        ChangeRequest changeRequest = requireOwningClientAndQuoted(changeRequestId);
        changeRequest.setStatus(ChangeRequestStatus.DECLINED);
        ChangeRequestDto dto = toDto(changeRequestRepository.save(changeRequest));

        projectActivityService.recordBestEffort(changeRequest.getEngagement().getId(), currentUserProvider.get(),
                ProjectActivityType.CHANGE_REQUEST_DECLINED,
                "Declined the quote for \"" + changeRequest.getTitle() + "\"", null);

        return dto;
    }

    @Transactional
    public ChangeRequestDto complete(UUID changeRequestId) {
        ChangeRequest changeRequest = getWithAccessCheck(changeRequestId);
        if (changeRequest.getStatus() != ChangeRequestStatus.ACCEPTED) {
            throw new BadRequestException("Only an accepted change request can be marked complete");
        }
        changeRequest.setStatus(ChangeRequestStatus.COMPLETED);
        ChangeRequestDto dto = toDto(changeRequestRepository.save(changeRequest));

        projectActivityService.recordBestEffort(changeRequest.getEngagement().getId(), currentUserProvider.get(),
                ProjectActivityType.CHANGE_REQUEST_COMPLETED,
                "Delivered change request \"" + changeRequest.getTitle() + "\"", null);

        return dto;
    }

    private ChangeRequest getWithAccessCheck(UUID changeRequestId) {
        ChangeRequest changeRequest = changeRequestRepository.findById(changeRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + changeRequestId));
        engagementService.getEntityWithAccessCheck(changeRequest.getEngagement().getId());
        return changeRequest;
    }

    /** Mirrors MilestoneApprovalService's client-only restriction -- see that class's javadoc. */
    private ChangeRequest requireOwningClientAndQuoted(UUID changeRequestId) {
        ChangeRequest changeRequest = changeRequestRepository.findById(changeRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found: " + changeRequestId));
        Engagement engagement = engagementService.getEntityWithAccessCheck(changeRequest.getEngagement().getId());

        User current = currentUserProvider.get();
        boolean isOwningClient = current.getRole() == Role.CLIENT && engagement.getClient().getId().equals(current.getId());
        if (!isOwningClient) {
            throw new AccessDeniedException("Only the project's client can accept or decline a change request quote");
        }
        if (changeRequest.getStatus() != ChangeRequestStatus.QUOTED) {
            throw new BadRequestException("This change request does not have a pending quote");
        }
        return changeRequest;
    }

    private ChangeRequestDto toDto(ChangeRequest c) {
        ProjectFileDto attachmentDto = null;
        if (c.getAttachment() != null) {
            ProjectFile f = c.getAttachment();
            // Same trade-off as ProjectMessageService#toDto: no signed URL resolved here,
            // the frontend fetches it on demand via the existing files endpoint using this id.
            attachmentDto = ProjectFileDto.builder()
                    .id(f.getId())
                    .fileName(f.getFileName())
                    .fileType(f.getFileType())
                    .fileSizeBytes(f.getFileSizeBytes())
                    .createdAt(f.getCreatedAt())
                    .build();
        }

        return ChangeRequestDto.builder()
                .id(c.getId())
                .engagementId(c.getEngagement().getId())
                .requestedByName(c.getRequestedBy().getFullName())
                .title(c.getTitle())
                .description(c.getDescription())
                .priority(c.getPriority())
                .status(c.getStatus())
                .estimatedCost(c.getEstimatedCost())
                .estimatedCostCurrency(c.getEstimatedCostCurrency())
                .estimatedTimelineDays(c.getEstimatedTimelineDays())
                .attachment(attachmentDto)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
