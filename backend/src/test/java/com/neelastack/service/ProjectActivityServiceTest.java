package com.neelastack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.ProjectActivityRepository;
import com.neelastack.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Two things matter most about this service: (1) the client-facing timeline must respect
 * the exact same "owner or staff" access rule as every other engagement-scoped endpoint,
 * even though it deliberately can't reuse EngagementService#getEntityWithAccessCheck (see
 * the class javadoc for why), and (2) a write failure here must never surface to -- let
 * alone roll back -- the real business operation that triggered it.
 */
class ProjectActivityServiceTest {

    private ProjectActivityRepository projectActivityRepository;
    private EngagementRepository engagementRepository;
    private CurrentUserProvider currentUserProvider;
    private ProjectActivityService projectActivityService;

    @BeforeEach
    void setUp() {
        projectActivityRepository = mock(ProjectActivityRepository.class);
        engagementRepository = mock(EngagementRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        projectActivityService = new ProjectActivityService(
                projectActivityRepository, engagementRepository, currentUserProvider, new ObjectMapper());
    }

    @Test
    void list_callerIsNeitherOwnerNorStaff_isDenied() {
        UUID engagementId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        User stranger = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        Engagement engagement = Engagement.builder().id(engagementId).client(owner).build();

        when(engagementRepository.findById(engagementId)).thenReturn(Optional.of(engagement));
        when(currentUserProvider.get()).thenReturn(stranger);

        assertThatThrownBy(() -> projectActivityService.list(engagementId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(projectActivityRepository);
    }

    @Test
    void list_ownerClient_isAllowed() {
        UUID engagementId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).role(Role.CLIENT).build();
        Engagement engagement = Engagement.builder().id(engagementId).client(owner).build();

        when(engagementRepository.findById(engagementId)).thenReturn(Optional.of(engagement));
        when(currentUserProvider.get()).thenReturn(owner);
        when(projectActivityRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId))
                .thenReturn(List.of());

        assertThatCode(() -> projectActivityService.list(engagementId)).doesNotThrowAnyException();
    }

    @Test
    void list_unknownEngagement_notFound() {
        UUID engagementId = UUID.randomUUID();
        when(engagementRepository.findById(engagementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectActivityService.list(engagementId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordBestEffort_repositorySaveThrows_swallowsExceptionInsteadOfPropagating() {
        UUID engagementId = UUID.randomUUID();
        when(engagementRepository.getReferenceById(engagementId))
                .thenReturn(Engagement.builder().id(engagementId).build());
        when(projectActivityRepository.save(any())).thenThrow(new RuntimeException("db is down"));

        // The whole point of #recordBestEffort: a broken timeline write must never bubble up
        // and fail the upload/milestone-update/payment that triggered it.
        assertThatCode(() -> projectActivityService.recordBestEffort(
                engagementId, null, ProjectActivityType.FILE_UPLOADED, "Uploaded report.pdf", null))
                .doesNotThrowAnyException();
    }
}
