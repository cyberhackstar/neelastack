package com.neelastack.service;

import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectFile;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProjectFileServiceTest {

    private ProjectFileRepository projectFileRepository;
    private EngagementService engagementService;
    private FileStorageService fileStorageService;
    private CurrentUserProvider currentUserProvider;
    private AuditLogService auditLogService;
    private ProjectFileService projectFileService;

    @BeforeEach
    void setUp() {
        projectFileRepository = mock(ProjectFileRepository.class);
        engagementService = mock(EngagementService.class);
        fileStorageService = mock(FileStorageService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        auditLogService = mock(AuditLogService.class);
        projectFileService = new ProjectFileService(
                projectFileRepository, engagementService, fileStorageService, currentUserProvider, auditLogService);
    }

    @Test
    void delete_fileBelongsToDifferentEngagement_isRejected() {
        // Caller has legitimate access to engagementId (the mock below simulates that), but
        // the fileId they supplied actually belongs to a completely different engagement —
        // this is exactly the gap the review flagged: access-to-*an*-engagement is not the
        // same as ownership-of-*this*-file.
        UUID callersEngagementId = UUID.randomUUID();
        Engagement someoneElsesEngagement = Engagement.builder().id(UUID.randomUUID()).build();

        ProjectFile file = ProjectFile.builder()
                .id(UUID.randomUUID())
                .engagement(someoneElsesEngagement)
                .cloudinaryPublicId("neelastack/engagements/other/secret-doc")
                .cloudinaryResourceType("image")
                .build();

        when(engagementService.getEntityWithAccessCheck(callersEngagementId))
                .thenReturn(Engagement.builder().id(callersEngagementId).build());
        when(projectFileRepository.findById(file.getId())).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> projectFileService.delete(callersEngagementId, file.getId()))
                .isInstanceOf(ResourceNotFoundException.class);

        // Must not touch Cloudinary or the DB row for a file that doesn't belong to this engagement.
        verifyNoInteractions(fileStorageService);
        verify(projectFileRepository, never()).delete(any());
    }

    @Test
    void delete_ownFile_deletesFromCloudinaryWithCorrectResourceType() {
        UUID engagementId = UUID.randomUUID();
        Engagement engagement = Engagement.builder().id(engagementId).build();

        ProjectFile file = ProjectFile.builder()
                .id(UUID.randomUUID())
                .engagement(engagement)
                .cloudinaryPublicId("neelastack/engagements/mine/report")
                .cloudinaryResourceType("raw")
                .build();

        when(engagementService.getEntityWithAccessCheck(engagementId)).thenReturn(engagement);
        when(projectFileRepository.findById(file.getId())).thenReturn(Optional.of(file));

        projectFileService.delete(engagementId, file.getId());

        // resource_type must be passed through — a bare publicId silently no-ops on
        // authenticated assets (this was the bug fixed in FileStorageService.delete).
        verify(fileStorageService).delete("neelastack/engagements/mine/report", "raw");
        verify(projectFileRepository).delete(file);
    }

    @Test
    void delete_unknownFileId_notFound() {
        UUID engagementId = UUID.randomUUID();
        UUID missingFileId = UUID.randomUUID();
        when(engagementService.getEntityWithAccessCheck(engagementId))
                .thenReturn(Engagement.builder().id(engagementId).build());
        when(projectFileRepository.findById(missingFileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectFileService.delete(engagementId, missingFileId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
