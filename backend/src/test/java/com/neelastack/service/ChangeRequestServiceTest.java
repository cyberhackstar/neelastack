package com.neelastack.service;

import com.neelastack.service.NotificationService;
import com.neelastack.dto.engagement.ChangeRequestCreateRequest;
import com.neelastack.dto.engagement.ChangeRequestQuoteRequest;
import com.neelastack.entity.ChangeRequest;
import com.neelastack.entity.ChangeRequestStatus;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectFile;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.ChangeRequestRepository;
import com.neelastack.repository.ProjectFileRepository;
import com.neelastack.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeRequestServiceTest {

    private ChangeRequestRepository changeRequestRepository;
    private ProjectFileRepository projectFileRepository;
    private EngagementService engagementService;
    private CurrentUserProvider currentUserProvider;
    private ProjectActivityService projectActivityService;
    private NotificationService notificationService;
    private ChangeRequestService changeRequestService;

    private Engagement engagement;
    private User client;
    private User admin;

    @BeforeEach
    void setUp() {
        changeRequestRepository = mock(ChangeRequestRepository.class);
        projectFileRepository = mock(ProjectFileRepository.class);
        engagementService = mock(EngagementService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        projectActivityService = mock(ProjectActivityService.class);
        notificationService = mock(NotificationService.class);
        changeRequestService = new ChangeRequestService(
                changeRequestRepository, projectFileRepository, engagementService, currentUserProvider,
                projectActivityService, notificationService);

        client = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).fullName("John Smith").build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).fullName("Bhawesh").build();
        engagement = Engagement.builder().id(UUID.randomUUID()).client(client).build();

        when(engagementService.getEntityWithAccessCheck(engagement.getId())).thenReturn(engagement);
        when(changeRequestRepository.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChangeRequest crWith(ChangeRequestStatus status) {
        ChangeRequest cr = ChangeRequest.builder()
                .id(UUID.randomUUID()).engagement(engagement).requestedBy(client)
                .title("Add WhatsApp login").description("...").status(status).build();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        return cr;
    }

    @Test
    void create_attachmentFromAnotherEngagement_isRejected() {
        when(currentUserProvider.get()).thenReturn(client);
        Engagement otherEngagement = Engagement.builder().id(UUID.randomUUID()).build();
        ProjectFile foreignFile = ProjectFile.builder().id(UUID.randomUUID()).engagement(otherEngagement).build();
        when(projectFileRepository.findById(foreignFile.getId())).thenReturn(Optional.of(foreignFile));

        var request = new ChangeRequestCreateRequest("Add login", "desc", null, foreignFile.getId());

        assertThatThrownBy(() -> changeRequestService.create(engagement.getId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void quote_onAlreadyAcceptedRequest_isRejected() {
        ChangeRequest cr = crWith(ChangeRequestStatus.ACCEPTED);
        var request = new ChangeRequestQuoteRequest(new BigDecimal("85000"), "INR", 14);

        assertThatThrownBy(() -> changeRequestService.quote(cr.getId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void quote_onSubmittedRequest_movesToQuoted() {
        ChangeRequest cr = crWith(ChangeRequestStatus.SUBMITTED);
        when(currentUserProvider.get()).thenReturn(admin);
        var request = new ChangeRequestQuoteRequest(new BigDecimal("85000"), "INR", 14);

        var dto = changeRequestService.quote(cr.getId(), request);

        assertThat(dto.status()).isEqualTo(ChangeRequestStatus.QUOTED);
        assertThat(dto.estimatedCost()).isEqualByComparingTo("85000");
        assertThat(dto.estimatedTimelineDays()).isEqualTo(14);
    }

    @Test
    void accept_whenNotYetQuoted_isRejected() {
        ChangeRequest cr = crWith(ChangeRequestStatus.SUBMITTED);
        when(currentUserProvider.get()).thenReturn(client);

        assertThatThrownBy(() -> changeRequestService.accept(cr.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void accept_byStaffRatherThanTheClient_isDenied() {
        ChangeRequest cr = crWith(ChangeRequestStatus.QUOTED);
        when(currentUserProvider.get()).thenReturn(admin);

        assertThatThrownBy(() -> changeRequestService.accept(cr.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accept_byOwningClientWhenQuoted_succeeds() {
        ChangeRequest cr = crWith(ChangeRequestStatus.QUOTED);
        when(currentUserProvider.get()).thenReturn(client);

        var dto = changeRequestService.accept(cr.getId());

        assertThat(dto.status()).isEqualTo(ChangeRequestStatus.ACCEPTED);
    }

    @Test
    void decline_byOwningClientWhenQuoted_succeeds() {
        ChangeRequest cr = crWith(ChangeRequestStatus.QUOTED);
        when(currentUserProvider.get()).thenReturn(client);

        var dto = changeRequestService.decline(cr.getId());

        assertThat(dto.status()).isEqualTo(ChangeRequestStatus.DECLINED);
    }

    @Test
    void complete_onAnythingOtherThanAccepted_isRejected() {
        ChangeRequest cr = crWith(ChangeRequestStatus.QUOTED);
        when(currentUserProvider.get()).thenReturn(admin);

        assertThatThrownBy(() -> changeRequestService.complete(cr.getId()))
                .isInstanceOf(BadRequestException.class);
    }
}
