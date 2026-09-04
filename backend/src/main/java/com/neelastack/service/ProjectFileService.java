package com.neelastack.service;

import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectFile;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectFileService {

    private final ProjectFileRepository projectFileRepository;
    private final EngagementService engagementService;
    private final FileStorageService fileStorageService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;

    public List<ProjectFileDto> list(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return projectFileRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ProjectFileDto upload(UUID engagementId, MultipartFile file) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);
        User uploader = currentUserProvider.get();

        FileStorageService.UploadResult result = fileStorageService.upload(
                file, "neelastack/engagements/" + engagementId
        );

        ProjectFile projectFile = ProjectFile.builder()
                .engagement(engagement)
                .uploadedBy(uploader)
                .fileName(file.getOriginalFilename())
                .fileUrl(result.url())
                .cloudinaryPublicId(result.publicId())
                .cloudinaryResourceType(result.resourceType())
                .fileType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .build();

        return toDto(projectFileRepository.save(projectFile));
    }

    @Transactional
    public void delete(UUID engagementId, UUID fileId) {
        engagementService.getEntityWithAccessCheck(engagementId);

        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        // Confirming the caller can access *an* engagement they belong to is not the same as
        // confirming this specific file belongs to *that* engagement. Without this check, a
        // client with legitimate access to their own engagement could delete a file belonging
        // to a completely different engagement, as long as they knew (or guessed/leaked) its
        // UUID — the earlier access check alone doesn't catch that.
        if (!file.getEngagement().getId().equals(engagementId)) {
            throw new ResourceNotFoundException("File not found: " + fileId);
        }

        fileStorageService.delete(file.getCloudinaryPublicId(), file.getCloudinaryResourceType());
        projectFileRepository.delete(file);
        auditLogService.recordBestEffort(AuditAction.FILE_DELETED, "ProjectFile", fileId.toString(),
                Map.of("engagementId", engagementId.toString(), "fileName", file.getFileName() == null ? "" : file.getFileName()));
    }

    private ProjectFileDto toDto(ProjectFile f) {
        // Regenerated fresh on every read rather than trusting the URL captured at upload
        // time — the signature is computed from the backend's own credentials right now,
        // so this always reflects a currently-valid signed link rather than one that could
        // have been generated under stale configuration.
        String signedUrl = fileStorageService.generateSignedUrl(f.getCloudinaryPublicId(), f.getCloudinaryResourceType());

        return ProjectFileDto.builder()
                .id(f.getId())
                .fileName(f.getFileName())
                .fileUrl(signedUrl)
                .fileType(f.getFileType())
                .fileSizeBytes(f.getFileSizeBytes())
                .uploadedByName(f.getUploadedBy().getFullName())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
