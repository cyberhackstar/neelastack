package com.neelastack.service;

import com.neelastack.dto.engagement.ProjectTaskDto;
import com.neelastack.dto.engagement.ProjectTaskRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.ProjectTask;
import com.neelastack.entity.ProjectTaskStatus;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.repository.ProjectTaskRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Section 12 of the client-workspace review: a milestone alone isn't enough to actually
 * manage delivery, so each milestone breaks down into concrete tasks with their own status,
 * owner and due date. Mutation endpoints (create/updateStatus/assign) are only ever reachable
 * through {@code /api/v1/admin/**}, enforced by SecurityConfig -- the access checks below are
 * defense in depth, mirroring how MilestoneService re-checks even though its own mutation
 * methods are equally admin-only.
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskService {

    private final ProjectTaskRepository projectTaskRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final EngagementService engagementService;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectActivityService projectActivityService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ProjectTaskDto> listForMilestone(UUID milestoneId) {
        Milestone milestone = getMilestoneWithAccessCheck(milestoneId);
        return projectTaskRepository.findByMilestoneIdOrderByDisplayOrderAsc(milestone.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskDto> listForEngagement(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return projectTaskRepository.findByEngagementId(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ProjectTaskDto create(UUID milestoneId, ProjectTaskRequest request) {
        Milestone milestone = getMilestoneWithAccessCheck(milestoneId);

        ProjectTask task = ProjectTask.builder()
                .milestone(milestone)
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .clientActionRequired(request.clientActionRequired() != null && request.clientActionRequired())
                .status(ProjectTaskStatus.TODO)
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();

        ProjectTaskDto dto = toDto(projectTaskRepository.save(task));

        projectActivityService.recordBestEffort(milestone.getEngagement().getId(), currentUserProvider.get(),
                ProjectActivityType.TASK_CREATED,
                "Added task \"" + request.title() + "\" to milestone \"" + milestone.getTitle() + "\"", null);

        if (task.isClientActionRequired()) {
            Engagement engagement = milestone.getEngagement();
            notificationService.notifyBestEffort(engagement.getClient(), engagement,
                    com.neelastack.entity.NotificationType.TASK_ACTION_REQUIRED,
                    "Action needed — " + task.getTitle(),
                    "A new task needs your input: \"" + task.getTitle() + "\".",
                    "/dashboard/" + engagement.getId() + "?tab=tasks");
        }

        return dto;
    }

    @Transactional
    public ProjectTaskDto updateStatus(UUID taskId, ProjectTaskStatus status) {
        ProjectTask task = projectTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        UUID engagementId = getMilestoneWithAccessCheck(task.getMilestone().getId()).getEngagement().getId();

        task.setStatus(status);
        ProjectTaskDto dto = toDto(projectTaskRepository.save(task));

        String summary = status == ProjectTaskStatus.DONE
                ? "Task \"" + task.getTitle() + "\" completed"
                : "Task \"" + task.getTitle() + "\" status changed to " + status.name().replace('_', ' ');
        projectActivityService.recordBestEffort(engagementId, currentUserProvider.get(),
                ProjectActivityType.TASK_STATUS_CHANGED, summary, null);

        return dto;
    }

    @Transactional
    public ProjectTaskDto assign(UUID taskId, String assigneeEmail) {
        ProjectTask task = projectTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        UUID engagementId = getMilestoneWithAccessCheck(task.getMilestone().getId()).getEngagement().getId();

        User assignee = null;
        if (assigneeEmail != null && !assigneeEmail.isBlank()) {
            assignee = userRepository.findByEmail(assigneeEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + assigneeEmail));
            boolean isStaff = assignee.getRole() == Role.ADMIN || assignee.getRole() == Role.SUPERADMIN;
            if (!isStaff) {
                throw new BadRequestException("Tasks can only be assigned to Neelastack staff");
            }
        }

        task.setAssignee(assignee);
        ProjectTaskDto dto = toDto(projectTaskRepository.save(task));

        String summary = assignee != null
                ? "Task \"" + task.getTitle() + "\" assigned to " + assignee.getFullName()
                : "Task \"" + task.getTitle() + "\" unassigned";
        projectActivityService.recordBestEffort(engagementId, currentUserProvider.get(),
                ProjectActivityType.TASK_ASSIGNED, summary, null);

        return dto;
    }

    private Milestone getMilestoneWithAccessCheck(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        // Also initializes the lazy engagement safely inside this transaction -- see
        // MilestoneService#updateStatus for the same reasoning.
        engagementService.getEntityWithAccessCheck(milestone.getEngagement().getId());
        return milestone;
    }

    private ProjectTaskDto toDto(ProjectTask t) {
        return ProjectTaskDto.builder()
                .id(t.getId())
                .milestoneId(t.getMilestone().getId())
                .engagementId(t.getMilestone().getEngagement().getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .dueDate(t.getDueDate())
                .clientActionRequired(t.isClientActionRequired())
                .assigneeName(t.getAssignee() != null ? t.getAssignee().getFullName() : null)
                .displayOrder(t.getDisplayOrder())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
