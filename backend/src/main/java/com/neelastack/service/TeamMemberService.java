package com.neelastack.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.neelastack.dto.content.TeamMemberDto;
import com.neelastack.entity.Role;
import com.neelastack.entity.TeamMember;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.TeamMemberRepository;
import com.neelastack.security.CurrentUserProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamMemberService {
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final TeamMemberRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Cloudinary cloudinary;
    private final Tika tika = new Tika();

    @Transactional
    public List<TeamMemberDto> listPublic() {
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public List<TeamMemberDto> listAdmin() {
        requireSuperAdmin();
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public TeamMemberDto create(String name, String role, String bio, String skills, Integer sortOrder,
                                Boolean active, MultipartFile photo) {
        requireSuperAdmin();
        validate(name, role, bio);
        if (photo == null || photo.isEmpty()) {
            throw new BadRequestException("Profile photo is required when creating a team member");
        }
        TeamMember member = TeamMember.builder()
                .name(name.trim()).role(role.trim()).bio(bio.trim()).skills(normalizeSkills(skills))
                .sortOrder(validateSortOrder(sortOrder))
                .active(active == null || active)
                .build();
        CloudImage image = upload(photo);
        member.setPhotoUrl(image.url());
        member.setPhotoPublicId(image.publicId());
        return toDto(repository.save(member));
    }

    @Transactional
    public TeamMemberDto update(UUID id, String name, String role, String bio, String skills, Integer sortOrder,
                                Boolean active, MultipartFile photo) {
        requireSuperAdmin();
        validate(name, role, bio);
        TeamMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found"));
        String oldPublicId = member.getPhotoPublicId();
        member.setName(name.trim());
        member.setRole(role.trim());
        member.setBio(bio.trim());
        member.setSkills(normalizeSkills(skills));
        member.setSortOrder(validateSortOrder(sortOrder));
        member.setActive(active == null || active);
        CloudImage image = null;
        if (photo != null && !photo.isEmpty()) {
            image = upload(photo);
            member.setPhotoUrl(image.url());
            member.setPhotoPublicId(image.publicId());
        }
        TeamMember saved = repository.save(member);
        if (image != null && oldPublicId != null && !oldPublicId.equals(image.publicId())) {
            deletePublicAsset(oldPublicId);
        }
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        requireSuperAdmin();
        TeamMember member = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found"));
        repository.delete(member);
        deletePublicAsset(member.getPhotoPublicId());
    }

    private void requireSuperAdmin() {
        if (currentUserProvider.get().getRole() != Role.SUPERADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("SUPERADMIN required");
        }
    }

    private void validate(String name, String role, String bio) {
        if (isBlank(name) || name.trim().length() > 120) throw new BadRequestException("Name is required and must be at most 120 characters");
        if (isBlank(role) || role.trim().length() > 120) throw new BadRequestException("Role is required and must be at most 120 characters");
        if (isBlank(bio) || bio.trim().length() > 4000) throw new BadRequestException("Bio is required and must be at most 4000 characters");
    }

    private int validateSortOrder(Integer sortOrder) {
        int value = sortOrder == null ? 0 : sortOrder;
        if (value < 0 || value > 100_000) {
            throw new BadRequestException("Display order must be between 0 and 100000");
        }
        return value;
    }

    private String normalizeSkills(String skills) {
        if (skills == null || skills.isBlank()) return "";
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(30)
                .map(s -> s.length() > 80 ? s.substring(0, 80) : s)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private CloudImage upload(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_SIZE) throw new BadRequestException("Team photo must be 5MB or smaller");
        try {
            byte[] bytes = file.getBytes();
            String type = tika.detect(bytes);
            if (!ALLOWED_IMAGE_TYPES.contains(type)) throw new BadRequestException("Only JPG, PNG and WebP team photos are allowed");
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "neelastack/team",
                    "resource_type", "image",
                    "type", "upload",
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false,
                    "quality", "auto:good",
                    "fetch_format", "auto"));
            return new CloudImage(String.valueOf(result.get("secure_url")), String.valueOf(result.get("public_id")));
        } catch (IOException e) {
            log.error("Cloudinary team image upload failed", e);
            throw new BadRequestException("Team photo upload failed — please try again");
        }
    }

    private void deletePublicAsset(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "type", "upload"));
        } catch (IOException e) {
            log.warn("Failed to delete team image {} from Cloudinary: {}", publicId, e.getMessage());
        }
    }

    private TeamMemberDto toDto(TeamMember m) {
        List<String> skills = m.getSkills() == null || m.getSkills().isBlank()
                ? List.of()
                : Arrays.stream(m.getSkills().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return TeamMemberDto.builder()
                .id(m.getId()).name(m.getName()).role(m.getRole()).bio(m.getBio()).skills(skills)
                .photoUrl(m.getPhotoUrl()).sortOrder(m.getSortOrder()).active(m.isActive())
                .createdAt(m.getCreatedAt()).updatedAt(m.getUpdatedAt()).build();
    }

    private boolean isBlank(String s) { return s == null || s.trim().isBlank(); }

    private record CloudImage(String url, String publicId) {}
}
