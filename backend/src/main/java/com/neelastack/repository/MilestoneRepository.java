package com.neelastack.repository;

import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByEngagementIdOrderByDisplayOrderAsc(UUID engagementId);

    // Powers ProjectHealthService — a milestone still open past its due date. Spring Data's
    // generated "dueDate < :cutoff" naturally excludes rows with a null dueDate (SQL's
    // three-valued logic), so undated milestones never falsely count as overdue.
    long countByEngagementIdAndStatusNotAndDueDateBefore(UUID engagementId, MilestoneStatus status, LocalDate cutoff);

    long countByEngagementIdAndStatus(UUID engagementId, MilestoneStatus status);

    long countByStatus(MilestoneStatus status);

    long countByStatusNotAndDueDateBetween(MilestoneStatus status, LocalDate from, LocalDate to);
}
