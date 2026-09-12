package com.neelastack.repository;

import com.neelastack.entity.ProjectTask;
import com.neelastack.entity.ProjectTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

    // Powers ProjectHealthService / the admin project-operations summary. Traverses
    // task -> milestone -> engagement via Spring Data's nested-property derivation.
    long countByMilestoneEngagementIdAndStatusNotAndDueDateBefore(UUID engagementId, ProjectTaskStatus status, LocalDate cutoff);

    long countByMilestoneEngagementIdAndClientActionRequiredTrueAndStatusNot(UUID engagementId, ProjectTaskStatus status);

    long countByStatusNotAndDueDateBefore(ProjectTaskStatus status, LocalDate cutoff);

    List<ProjectTask> findByMilestoneIdOrderByDisplayOrderAsc(UUID milestoneId);

    // Powers the single "all tasks for this project" read both the client Overview/Tasks
    // section and the admin project page use — a client or admin shouldn't have to fetch
    // every milestone first just to see the full task list. JOIN FETCH here (unlike
    // findByMilestoneIdOrderByDisplayOrderAsc, where every row shares one already-known
    // milestone) because this can span many distinct milestones, and toDto() needs each
    // task's engagement id — without the fetch, that's an N+1 initializing each lazy
    // Milestone proxy in turn.
    @Query("SELECT t FROM ProjectTask t JOIN FETCH t.milestone m JOIN FETCH m.engagement " +
           "WHERE m.engagement.id = :engagementId ORDER BY m.displayOrder ASC, t.displayOrder ASC")
    List<ProjectTask> findByEngagementId(@Param("engagementId") UUID engagementId);
}
