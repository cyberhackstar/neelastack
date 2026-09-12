package com.neelastack.controller;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.ChangeRequestCreateRequest;
import com.neelastack.dto.engagement.ChangeRequestDto;
import com.neelastack.dto.engagement.MilestoneApprovalDto;
import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.ProjectActivityDto;
import com.neelastack.dto.engagement.ProjectFileDto;
import com.neelastack.dto.engagement.ProjectMessageDto;
import com.neelastack.dto.engagement.ProjectMessageRequest;
import com.neelastack.dto.engagement.ProjectTaskDto;
import com.neelastack.dto.engagement.RequestMilestoneChangesRequest;
import com.neelastack.dto.engagement.UnreadCountDto;
import com.neelastack.service.ChangeRequestService;
import com.neelastack.service.EngagementService;
import com.neelastack.service.MilestoneApprovalService;
import com.neelastack.service.MilestoneService;
import com.neelastack.service.ProjectActivityService;
import com.neelastack.service.ProjectFileService;
import com.neelastack.service.ProjectMessageService;
import com.neelastack.service.ProjectTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Client-scoped engagement APIs. Ownership is enforced inside the service layer on every call.
 */
@RestController
@RequestMapping("/api/v1/client/engagements")
@RequiredArgsConstructor
@Tag(name = "Client dashboard", description = "Requires authentication. Returns only the caller's own engagements.")
public class ClientEngagementController {

    private final EngagementService engagementService;
    private final MilestoneService milestoneService;
    private final ProjectFileService projectFileService;
    private final ProjectMessageService projectMessageService;
    private final ProjectActivityService projectActivityService;
    private final ProjectTaskService projectTaskService;
    private final MilestoneApprovalService milestoneApprovalService;
    private final ChangeRequestService changeRequestService;

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

    @GetMapping("/{id}/activity")
    public List<ProjectActivityDto> activity(@PathVariable UUID id) {
        return projectActivityService.list(id);
    }

    @GetMapping("/{id}/tasks")
    public List<ProjectTaskDto> tasks(@PathVariable UUID id) {
        return projectTaskService.listForEngagement(id);
    }

    @GetMapping("/{id}/milestone-approvals")
    public List<MilestoneApprovalDto> milestoneApprovals(@PathVariable UUID id) {
        return milestoneApprovalService.listForEngagement(id);
    }

    @PostMapping("/milestones/{milestoneId}/approve")
    public MilestoneDto approveMilestone(@PathVariable UUID milestoneId) {
        return milestoneApprovalService.approve(milestoneId);
    }

    @PostMapping("/milestones/{milestoneId}/request-changes")
    public MilestoneDto requestMilestoneChanges(@PathVariable UUID milestoneId, @Valid @RequestBody RequestMilestoneChangesRequest request) {
        return milestoneApprovalService.requestChanges(milestoneId, request.comment());
    }

    @GetMapping("/{id}/change-requests")
    public List<ChangeRequestDto> changeRequests(@PathVariable UUID id) {
        return changeRequestService.list(id);
    }

    @PostMapping("/{id}/change-requests")
    public ResponseEntity<ChangeRequestDto> submitChangeRequest(@PathVariable UUID id, @Valid @RequestBody ChangeRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(changeRequestService.create(id, request));
    }

    @PostMapping("/change-requests/{changeRequestId}/accept")
    public ChangeRequestDto acceptChangeRequest(@PathVariable UUID changeRequestId) {
        return changeRequestService.accept(changeRequestId);
    }

    @PostMapping("/change-requests/{changeRequestId}/decline")
    public ChangeRequestDto declineChangeRequest(@PathVariable UUID changeRequestId) {
        return changeRequestService.decline(changeRequestId);
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

    @GetMapping("/{id}/messages")
    public List<ProjectMessageDto> messages(@PathVariable UUID id) {
        return projectMessageService.list(id);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ProjectMessageDto> sendMessage(@PathVariable UUID id, @Valid @RequestBody ProjectMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMessageService.send(id, request));
    }

    @PostMapping("/{id}/messages/read")
    public ResponseEntity<Void> markMessagesRead(@PathVariable UUID id) {
        projectMessageService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages/unread-count")
    public UnreadCountDto unreadMessageCount(@PathVariable UUID id) {
        return projectMessageService.unreadCount(id);
    }
}
