package com.neelastack.service;

import com.neelastack.service.NotificationService;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.MilestoneApprovalRepository;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MilestoneApprovalServiceTest {

    private MilestoneApprovalRepository milestoneApprovalRepository;
    private MilestoneRepository milestoneRepository;
    private EngagementService engagementService;
    private CurrentUserProvider currentUserProvider;
    private ProjectActivityService projectActivityService;
    private NotificationService notificationService;
    private MilestoneApprovalService milestoneApprovalService;

    private Engagement engagement;
    private User client;
    private User admin;

    @BeforeEach
    void setUp() {
        milestoneApprovalRepository = mock(MilestoneApprovalRepository.class);
        milestoneRepository = mock(MilestoneRepository.class);
        engagementService = mock(EngagementService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        projectActivityService = mock(ProjectActivityService.class);
        notificationService = mock(NotificationService.class);
        milestoneApprovalService = new MilestoneApprovalService(
                milestoneApprovalRepository, milestoneRepository, engagementService, currentUserProvider,
                projectActivityService, notificationService);

        client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).fullName("John Smith").build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).fullName("Bhawesh").build();
        engagement = Engagement.builder().id(UUID.randomUUID()).client(client).build();

        when(engagementService.getEntityWithAccessCheck(engagement.getId())).thenReturn(engagement);
        when(milestoneApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Milestone milestoneWith(MilestoneStatus status) {
        Milestone m = Milestone.builder().id(UUID.randomUUID()).engagement(engagement).title("API integration")
                .status(status).build();
        when(milestoneRepository.findById(m.getId())).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void approve_whenAwaitingApprovalAndCallerIsOwningClient_marksDone() {
        Milestone milestone = milestoneWith(MilestoneStatus.AWAITING_APPROVAL);
        when(currentUserProvider.get()).thenReturn(client);

        var dto = milestoneApprovalService.approve(milestone.getId());

        assertThat(dto.status()).isEqualTo(MilestoneStatus.DONE);
    }

    @Test
    void approve_whenNotAwaitingApproval_isRejected() {
        Milestone milestone = milestoneWith(MilestoneStatus.IN_PROGRESS);
        when(currentUserProvider.get()).thenReturn(client);

        assertThatThrownBy(() -> milestoneApprovalService.approve(milestone.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void approve_whenCallerIsStaffNotTheClient_isDenied() {
        Milestone milestone = milestoneWith(MilestoneStatus.AWAITING_APPROVAL);
        when(currentUserProvider.get()).thenReturn(admin);

        assertThatThrownBy(() -> milestoneApprovalService.approve(milestone.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requestChanges_recordsCommentAndSetsChangesRequested() {
        Milestone milestone = milestoneWith(MilestoneStatus.AWAITING_APPROVAL);
        when(currentUserProvider.get()).thenReturn(client);

        var dto = milestoneApprovalService.requestChanges(milestone.getId(),
                "Please make the hero section more compact on mobile.");

        assertThat(dto.status()).isEqualTo(MilestoneStatus.CHANGES_REQUESTED);
    }

    @Test
    void requestChanges_whenCallerIsADifferentClient_isDenied() {
        Milestone milestone = milestoneWith(MilestoneStatus.AWAITING_APPROVAL);
        User stranger = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        when(currentUserProvider.get()).thenReturn(stranger);

        assertThatThrownBy(() -> milestoneApprovalService.requestChanges(milestone.getId(), "Not my project"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
