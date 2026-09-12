package com.neelastack.service;

import com.neelastack.service.NotificationService;
import com.neelastack.dto.engagement.ProjectTaskRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTaskServiceTest {

    private ProjectTaskRepository projectTaskRepository;
    private MilestoneRepository milestoneRepository;
    private UserRepository userRepository;
    private EngagementService engagementService;
    private CurrentUserProvider currentUserProvider;
    private ProjectActivityService projectActivityService;
    private NotificationService notificationService;
    private ProjectTaskService projectTaskService;

    private Engagement engagement;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        projectTaskRepository = mock(ProjectTaskRepository.class);
        milestoneRepository = mock(MilestoneRepository.class);
        userRepository = mock(UserRepository.class);
        engagementService = mock(EngagementService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        projectActivityService = mock(ProjectActivityService.class);
        notificationService = mock(NotificationService.class);
        projectTaskService = new ProjectTaskService(
                projectTaskRepository, milestoneRepository, userRepository, engagementService,
                currentUserProvider, projectActivityService, notificationService);

        engagement = Engagement.builder().id(UUID.randomUUID()).build();
        milestone = Milestone.builder().id(UUID.randomUUID()).engagement(engagement).title("Backend modernization")
                .build();
        when(engagementService.getEntityWithAccessCheck(engagement.getId())).thenReturn(engagement);
        when(milestoneRepository.findById(milestone.getId())).thenReturn(Optional.of(milestone));
        when(currentUserProvider.get())
                .thenReturn(User.builder().id(UUID.randomUUID()).role(Role.ADMIN).fullName("Bhawesh").build());
        when(projectTaskRepository.save(any(ProjectTask.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_assignsToMilestoneAndReturnsDefaultTodoStatus() {
        var request = new ProjectTaskRequest("Payment integration", "Wire up Razorpay", null, true, 1);

        var dto = projectTaskService.create(milestone.getId(), request);

        assertThat(dto.status()).isEqualTo(ProjectTaskStatus.TODO);
        assertThat(dto.clientActionRequired()).isTrue();
        assertThat(dto.engagementId()).isEqualTo(engagement.getId());
    }

    @Test
    void assign_toNonStaffUser_isRejected() {
        ProjectTask task = ProjectTask.builder().id(UUID.randomUUID()).milestone(milestone).title("QA pass").build();
        when(projectTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        User clientUser = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).email("client@acme.com").build();
        when(userRepository.findByEmail("client@acme.com")).thenReturn(Optional.of(clientUser));

        assertThatThrownBy(() -> projectTaskService.assign(task.getId(), "client@acme.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("staff");
    }

    @Test
    void assign_nullEmail_unassignsTask() {
        User previousAssignee = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).fullName("Bhawesh").build();
        ProjectTask task = ProjectTask.builder().id(UUID.randomUUID()).milestone(milestone)
                .title("Database migration").assignee(previousAssignee).build();
        when(projectTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        var dto = projectTaskService.assign(task.getId(), null);

        assertThat(dto.assigneeName()).isNull();
    }

    @Test
    void assign_unknownEmail_notFound() {
        ProjectTask task = ProjectTask.builder().id(UUID.randomUUID()).milestone(milestone).title("QA pass").build();
        when(projectTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(userRepository.findByEmail("ghost@neelastack.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectTaskService.assign(task.getId(), "ghost@neelastack.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_toDone_producesCompletedSummary() {
        ProjectTask task = ProjectTask.builder().id(UUID.randomUUID()).milestone(milestone).title("API integration")
                .build();
        when(projectTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        var dto = projectTaskService.updateStatus(task.getId(), ProjectTaskStatus.DONE);

        assertThat(dto.status()).isEqualTo(ProjectTaskStatus.DONE);
    }
}
