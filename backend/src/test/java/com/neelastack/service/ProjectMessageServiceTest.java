package com.neelastack.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProjectMessageServiceTest {

    private ProjectMessageRepository projectMessageRepository;
    private ProjectMessageReadRepository projectMessageReadRepository;
    private ProjectFileRepository projectFileRepository;
    private EngagementService engagementService;
    private CurrentUserProvider currentUserProvider;
    private ProjectMessageService projectMessageService;

    @BeforeEach
    void setUp() {
        projectMessageRepository = mock(ProjectMessageRepository.class);
        projectMessageReadRepository = mock(ProjectMessageReadRepository.class);
        projectFileRepository = mock(ProjectFileRepository.class);
        engagementService = mock(EngagementService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        projectMessageService = new ProjectMessageService(
                projectMessageRepository, projectMessageReadRepository, projectFileRepository,
                engagementService, currentUserProvider);

        // save() echoes back whatever entity it was given, like a real repository would.
        when(projectMessageRepository.save(any(ProjectMessage.class)))
                .thenAnswer(inv -> {
                    ProjectMessage m = inv.getArgument(0);
                    if (m.getId() == null) m.setId(UUID.randomUUID());
                    return m;
                });
        when(projectMessageReadRepository.save(any(ProjectMessageRead.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void send_plainTextMessage_isSavedWithSenderAndMarksThreadRead() {
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();
        User client = User.builder().id(UUID.randomUUID()).fullName("Jane Client").role(Role.CLIENT).build();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(currentUserProvider.get()).thenReturn(client);
        when(projectMessageReadRepository.findByEngagementIdAndUserId(engagementId, client.getId()))
                .thenReturn(Optional.empty());

        ProjectMessageDto dto = projectMessageService.send(engagementId, new ProjectMessageRequest("Please review the auth flow", null));

        assertThat(dto.body()).isEqualTo("Please review the auth flow");
        assertThat(dto.senderName()).isEqualTo("Jane Client");
        assertThat(dto.senderRole()).isEqualTo("CLIENT");
        assertThat(dto.attachment()).isNull();

        // Sending counts as catching up on the thread yourself.
        verify(projectMessageReadRepository).save(any(ProjectMessageRead.class));
    }

    @Test
    void send_withAttachmentOnSameEngagement_includesAttachmentInResponse() {
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();
        User staff = User.builder().id(UUID.randomUUID()).fullName("Bhawesh").role(Role.ADMIN).build();
        ProjectFile attachment = ProjectFile.builder()
                .id(UUID.randomUUID())
                .engagement(engagement)
                .fileName("architecture-v2.pdf")
                .build();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(currentUserProvider.get()).thenReturn(staff);
        when(projectFileRepository.findById(attachment.getId())).thenReturn(Optional.of(attachment));
        when(projectMessageReadRepository.findByEngagementIdAndUserId(engagementId, staff.getId()))
                .thenReturn(Optional.empty());

        ProjectMessageDto dto = projectMessageService.send(
                engagementId, new ProjectMessageRequest("Reviewed, looks good", attachment.getId()));

        assertThat(dto.senderRole()).isEqualTo("STAFF");
        assertThat(dto.attachment()).isNotNull();
        assertThat(dto.attachment().fileName()).isEqualTo("architecture-v2.pdf");
    }

    @Test
    void send_withAttachmentFromAnotherEngagement_isRejected() {
        // Mirrors the ProjectFile ownership fix: engagement-level access to *an* engagement
        // must not let a message on engagement A reference (and thereby expose) a file that
        // actually belongs to engagement B.
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();
        Engagement someoneElsesEngagement = Engagement.builder().id(UUID.randomUUID()).build();
        User client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        ProjectFile foreignFile = ProjectFile.builder()
                .id(UUID.randomUUID())
                .engagement(someoneElsesEngagement)
                .fileName("secret-doc.pdf")
                .build();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(currentUserProvider.get()).thenReturn(client);
        when(projectFileRepository.findById(foreignFile.getId())).thenReturn(Optional.of(foreignFile));

        assertThatThrownBy(() -> projectMessageService.send(
                engagementId, new ProjectMessageRequest("here's the file", foreignFile.getId())))
                .isInstanceOf(BadRequestException.class);

        verify(projectMessageRepository, never()).save(any());
    }

    @Test
    void send_withUnknownAttachmentId_notFound() {
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();
        User client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        UUID missingFileId = UUID.randomUUID();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(currentUserProvider.get()).thenReturn(client);
        when(projectFileRepository.findById(missingFileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectMessageService.send(
                engagementId, new ProjectMessageRequest("here's the file", missingFileId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unreadCount_neverReadBefore_countsEveryoneElsesMessages() {
        UUID engagementId = UUID.randomUUID();
        User client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();

        when(engagementService.getEntityWithAccessCheck(engagementId))
                .thenReturn(Engagement.builder().id(engagementId).build());
        when(currentUserProvider.get()).thenReturn(client);
        when(projectMessageReadRepository.findByEngagementIdAndUserId(engagementId, client.getId()))
                .thenReturn(Optional.empty());
        when(projectMessageRepository.countByEngagementIdAndSenderIdNotAndCreatedAtAfter(
                eq(engagementId), eq(client.getId()), any(LocalDateTime.class)))
                .thenReturn(3L);

        UnreadCountDto result = projectMessageService.unreadCount(engagementId);

        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    @Test
    void unreadCount_afterMarkingRead_usesTheStoredLastReadAtAsTheCutoff() {
        UUID engagementId = UUID.randomUUID();
        User client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        LocalDateTime lastRead = LocalDateTime.now().minusHours(2);
        ProjectMessageRead readState = ProjectMessageRead.builder()
                .id(UUID.randomUUID())
                .lastReadAt(lastRead)
                .build();

        when(engagementService.getEntityWithAccessCheck(engagementId))
                .thenReturn(Engagement.builder().id(engagementId).build());
        when(currentUserProvider.get()).thenReturn(client);
        when(projectMessageReadRepository.findByEngagementIdAndUserId(engagementId, client.getId()))
                .thenReturn(Optional.of(readState));
        when(projectMessageRepository.countByEngagementIdAndSenderIdNotAndCreatedAtAfter(
                engagementId, client.getId(), lastRead))
                .thenReturn(1L);

        UnreadCountDto result = projectMessageService.unreadCount(engagementId);

        assertThat(result.unreadCount()).isEqualTo(1L);
        verify(projectMessageRepository).countByEngagementIdAndSenderIdNotAndCreatedAtAfter(
                engagementId, client.getId(), lastRead);
    }

    @Test
    void markRead_noExistingReadState_createsOne() {
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();
        User client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(currentUserProvider.get()).thenReturn(client);
        when(projectMessageReadRepository.findByEngagementIdAndUserId(engagementId, client.getId()))
                .thenReturn(Optional.empty());

        projectMessageService.markRead(engagementId);

        verify(projectMessageReadRepository).save(argThat(saved ->
                saved.getEngagement().getId().equals(engagementId)
                        && saved.getUser().getId().equals(client.getId())
                        && saved.getLastReadAt() != null));
    }
}
