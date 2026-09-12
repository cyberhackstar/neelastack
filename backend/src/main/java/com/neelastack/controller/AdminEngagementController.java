package com.neelastack.controller;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.EngagementRequest;
import com.neelastack.dto.engagement.EngagementStatusUpdateRequest;
import com.neelastack.dto.engagement.ChangeRequestDto;
import com.neelastack.dto.engagement.ChangeRequestQuoteRequest;
import com.neelastack.dto.engagement.MilestoneApprovalDto;
import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.MilestoneRequest;
import com.neelastack.dto.engagement.MilestoneStatusUpdateRequest;
import com.neelastack.dto.engagement.ProjectActivityDto;
import com.neelastack.dto.engagement.ProjectMessageDto;
import com.neelastack.dto.engagement.ProjectMessageRequest;
import com.neelastack.dto.engagement.ProjectTaskAssignRequest;
import com.neelastack.dto.engagement.ProjectTaskDto;
import com.neelastack.dto.engagement.ProjectTaskRequest;
import com.neelastack.dto.engagement.ProjectTaskStatusUpdateRequest;
import com.neelastack.dto.engagement.UnreadCountDto;
import com.neelastack.service.ChangeRequestService;
import com.neelastack.service.EngagementService;
import com.neelastack.service.MilestoneApprovalService;
import com.neelastack.service.MilestoneService;
import com.neelastack.service.ProjectActivityService;
import com.neelastack.service.ProjectMessageService;
import com.neelastack.service.ProjectTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/engagements")
@RequiredArgsConstructor
@Tag(name = "Admin — engagements", description = "Requires ROLE_ADMIN. If the client email has no account yet, one is created as a pending invitation and the client is emailed a link to activate it — no prior registration required.")
public class AdminEngagementController {

    private final EngagementService engagementService;
    private final MilestoneService milestoneService;
    private final ProjectMessageService projectMessageService;
    private final ProjectActivityService projectActivityService;
    private final ProjectTaskService projectTaskService;
    private final MilestoneApprovalService milestoneApprovalService;
    private final ChangeRequestService changeRequestService;

    @GetMapping
    public List<EngagementDto> listAll() {
        return engagementService.listAllForAdmin();
    }

    @PostMapping
    public ResponseEntity<EngagementDto> create(@Valid @RequestBody EngagementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engagementService.create(request));
    }

    @PatchMapping("/{id}/status")
    public EngagementDto updateStatus(@PathVariable UUID id, @Valid @RequestBody EngagementStatusUpdateRequest request) {
        return engagementService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/milestones")
    public ResponseEntity<MilestoneDto> addMilestone(@PathVariable UUID id, @Valid @RequestBody MilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.create(id, request));
    }

    @PatchMapping("/milestones/{milestoneId}/status")
    public MilestoneDto updateMilestoneStatus(@PathVariable UUID milestoneId, @Valid @RequestBody MilestoneStatusUpdateRequest request) {
        return milestoneService.updateStatus(milestoneId, request.status());
    }

    @GetMapping("/{id}/activity")
    public List<ProjectActivityDto> activity(@PathVariable UUID id) {
        return projectActivityService.list(id);
    }

    @GetMapping("/{id}/tasks")
    public List<ProjectTaskDto> tasks(@PathVariable UUID id) {
        return projectTaskService.listForEngagement(id);
    }

    @PostMapping("/milestones/{milestoneId}/tasks")
    public ResponseEntity<ProjectTaskDto> addTask(@PathVariable UUID milestoneId, @Valid @RequestBody ProjectTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectTaskService.create(milestoneId, request));
    }

    @PatchMapping("/tasks/{taskId}/status")
    public ProjectTaskDto updateTaskStatus(@PathVariable UUID taskId, @Valid @RequestBody ProjectTaskStatusUpdateRequest request) {
        return projectTaskService.updateStatus(taskId, request.status());
    }

    @PatchMapping("/tasks/{taskId}/assign")
    public ProjectTaskDto assignTask(@PathVariable UUID taskId, @RequestBody ProjectTaskAssignRequest request) {
        return projectTaskService.assign(taskId, request.assigneeEmail());
    }

    // Read-only: approve/request-changes are intentionally client-only, see
    // MilestoneApprovalService's class javadoc. Staff can see the trail, not make the call.
    @GetMapping("/{id}/milestone-approvals")
    public List<MilestoneApprovalDto> milestoneApprovals(@PathVariable UUID id) {
        return milestoneApprovalService.listForEngagement(id);
    }

    @GetMapping("/{id}/change-requests")
    public List<ChangeRequestDto> changeRequests(@PathVariable UUID id) {
        return changeRequestService.list(id);
    }

    @PatchMapping("/change-requests/{changeRequestId}/quote")
    public ChangeRequestDto quoteChangeRequest(@PathVariable UUID changeRequestId, @Valid @RequestBody ChangeRequestQuoteRequest request) {
        return changeRequestService.quote(changeRequestId, request);
    }

    @PostMapping("/change-requests/{changeRequestId}/complete")
    public ChangeRequestDto completeChangeRequest(@PathVariable UUID changeRequestId) {
        return changeRequestService.complete(changeRequestId);
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
