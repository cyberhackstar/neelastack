package com.neelastack.service;

import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.dto.engagement.ProjectMessageDto;
import com.neelastack.dto.engagement.ProjectMessageRequest;
import com.neelastack.dto.engagement.UnreadCountDto;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectFile;
import com.neelastack.entity.ProjectMessage;
import com.neelastack.entity.ProjectMessageRead;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.repository.ProjectMessageReadRepository;
import com.neelastack.repository.ProjectMessageRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * In-project chat between a client and Neelastack staff, scoped to a single engagement.
 * Every operation reuses {@link EngagementService#getEntityWithAccessCheck} so a message
 * thread is only reachable by the client who owns the engagement or by staff — the exact
 * same access rule the files and milestones endpoints already enforce.
 */
@Service
@RequiredArgsConstructor
public class ProjectMessageService {

    private final ProjectMessageRepository projectMessageRepository;
    private final ProjectMessageReadRepository projectMessageReadRepository;
    private final ProjectFileRepository projectFileRepository;
    private final EngagementService engagementService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<ProjectMessageDto> list(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return projectMessageRepository.findByEngagementIdOrderByCreatedAtAsc(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ProjectMessageDto send(UUID engagementId, ProjectMessageRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);
        User sender = currentUserProvider.get();

        ProjectFile attachment = null;
        if (request.attachmentFileId() != null) {
            attachment = projectFileRepository.findById(request.attachmentFileId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "File not found: " + request.attachmentFileId()));
            // Same class of check as ProjectFileService#delete: the caller having access to
            // *an* engagement doesn't mean the fileId they supplied belongs to *this*
            // engagement. Without this, a message on engagement A could point at (and thereby
            // expose a signed link to) a file that actually lives on engagement B.
            if (!attachment.getEngagement().getId().equals(engagementId)) {
                throw new BadRequestException(
                        "That file does not belong to this project");
            }
        }

        ProjectMessage message = ProjectMessage.builder()
                .engagement(engagement)
                .sender(sender)
                .body(request.body())
                .attachment(attachment)
                .build();

        ProjectMessage saved = projectMessageRepository.save(message);

        // Sending a message also counts as having caught up on the thread yourself, so your
        // own message never shows up as unread in your own badge.
        markRead(engagementId);

        return toDto(saved);
    }

    @Transactional
    public void markRead(UUID engagementId) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);
        User current = currentUserProvider.get();

        ProjectMessageRead readState = projectMessageReadRepository
                .findByEngagementIdAndUserId(engagementId, current.getId())
                .orElseGet(() -> ProjectMessageRead.builder()
                        .engagement(engagement)
                        .user(current)
                        .build());

        readState.setLastReadAt(LocalDateTime.now());
        projectMessageReadRepository.save(readState);
    }

    @Transactional(readOnly = true)
    public UnreadCountDto unreadCount(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        User current = currentUserProvider.get();

        LocalDateTime since = projectMessageReadRepository
                .findByEngagementIdAndUserId(engagementId, current.getId())
                .map(ProjectMessageRead::getLastReadAt)
                // Never read before: everything anyone else has ever sent on this
                // engagement is unread.
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));

        long count = projectMessageRepository.countByEngagementIdAndSenderIdNotAndCreatedAtAfter(
                engagementId, current.getId(), since);
        return UnreadCountDto.builder().unreadCount(count).build();
    }

    private ProjectMessageDto toDto(ProjectMessage m) {
        ProjectFileDto attachmentDto = null;
        if (m.getAttachment() != null) {
            ProjectFile f = m.getAttachment();
            attachmentDto = ProjectFileDto.builder()
                    .id(f.getId())
                    .fileName(f.getFileName())
                    // Deliberately not resolving a signed URL here: that requires a Cloudinary
                    // round trip per attachment and the message list can be long. Clients
                    // fetch the signed link on demand via the existing files endpoint using
                    // this id, exactly like the document center does.
                    .fileType(f.getFileType())
                    .fileSizeBytes(f.getFileSizeBytes())
                    .createdAt(f.getCreatedAt())
                    .build();
        }

        User sender = m.getSender();
        return ProjectMessageDto.builder()
                .id(m.getId())
                .engagementId(m.getEngagement().getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .senderRole(sender.getRole() == Role.CLIENT ? "CLIENT" : "STAFF")
                .body(m.getBody())
                .attachment(attachmentDto)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
