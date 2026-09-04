package com.neelastack.controller;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.service.EngagementService;
import com.neelastack.service.MilestoneService;
import com.neelastack.service.ProjectFileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing endpoints. Any authenticated user can call these — ownership
 * (the caller is either the engagement's client or an admin) is enforced
 * inside the service layer on every call.
 */
@RestController
@RequestMapping("/api/v1/client/engagements")
@RequiredArgsConstructor
@Tag(name = "Client dashboard", description = "Requires authentication. Returns only the caller's own engagements (or all, for admins).")
public class ClientEngagementController {

    private final EngagementService engagementService;
    private final MilestoneService milestoneService;
    private final ProjectFileService projectFileService;

    @GetMapping
    public List<EngagementDto> myEngagements() {
        return engagementService.listForCurrentClient();
    }

    @GetMapping("/{id}")
    public EngagementDto get(@PathVariable UUID id) {
        return engagementService.get(id);
    }

    @GetMapping("/{id}/milestones")
    public List<MilestoneDto> milestones(@PathVariable UUID id) {
        return milestoneService.list(id);
    }

    @GetMapping("/{id}/files")
    public List<ProjectFileDto> files(@PathVariable UUID id) {
        return projectFileService.list(id);
    }

    @PostMapping(value = "/{id}/files", consumes = "multipart/form-data")
    public ResponseEntity<ProjectFileDto> uploadFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectFileService.upload(id, file));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        projectFileService.delete(id, fileId);
        return ResponseEntity.noContent().build();
    }
}
