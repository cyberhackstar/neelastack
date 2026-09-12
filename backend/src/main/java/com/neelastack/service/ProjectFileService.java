package com.neelastack.service;

import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.ProjectFile;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final ProjectActivityService projectActivityService;

    // Read-only transaction: toDto() below reads f.getUploadedBy().getFullName(), and
    // ProjectFile.uploadedBy is @ManyToOne(LAZY). With open-in-view=false, an untransactional
    // read here throws LazyInitializationException the moment traffic actually hits this path —
    // same class of bug as the Quotation/Invoice fixes above.
    @Transactional(readOnly = true)
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

        ProjectFileDto dto = toDto(projectFileRepository.save(projectFile));

        projectActivityService.recordBestEffort(engagementId, uploader,
                ProjectActivityType.FILE_UPLOADED, "Uploaded " + dto.fileName(), null);

        return dto;
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

        // Engagement-level access only proves the caller belongs to this project, not that
        // they own this particular file. Admin/superadmin staff manage the whole project and
        // may remove any file in it, but a CLIENT must be restricted to deleting files they
        // themselves uploaded — otherwise any client on the engagement could delete files
        // uploaded by Neelastack staff (deliverables, internal docs) or by other client-side
        // collaborators on the same engagement. This is the invariant the docs already promise
        // but the code did not enforce.
        User current = currentUserProvider.get();
        boolean isStaff = current.getRole() == Role.ADMIN || current.getRole() == Role.SUPERADMIN;
        boolean isUploader = file.getUploadedBy() != null && file.getUploadedBy().getId().equals(current.getId());
        if (!isStaff && !isUploader) {
            throw new AccessDeniedException("You can only delete files you uploaded yourself");
        }

        String fileName = file.getFileName() == null ? "" : file.getFileName();

        fileStorageService.delete(file.getCloudinaryPublicId(), file.getCloudinaryResourceType());
        projectFileRepository.delete(file);
        auditLogService.recordBestEffort(AuditAction.FILE_DELETED, "ProjectFile", fileId.toString(),
                Map.of("engagementId", engagementId.toString(), "fileName", fileName));
        projectActivityService.recordBestEffort(engagementId, current,
                ProjectActivityType.FILE_DELETED, "Removed " + fileName, null);
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
