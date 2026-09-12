package com.neelastack.repository;

import com.neelastack.entity.MilestoneApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MilestoneApprovalRepository extends JpaRepository<MilestoneApproval, UUID> {

    // JOIN FETCH for the same reason as ProjectTaskRepository#findByEngagementId: this spans
    // every milestone in the project, so toDto() needs each row's engagement id without an
    // N+1 initializing each lazy Milestone proxy in turn.
    @Query("SELECT a FROM MilestoneApproval a JOIN FETCH a.milestone m JOIN FETCH m.engagement " +
           "WHERE m.engagement.id = :engagementId ORDER BY a.createdAt DESC")
    List<MilestoneApproval> findByEngagementId(@Param("engagementId") UUID engagementId);
}
