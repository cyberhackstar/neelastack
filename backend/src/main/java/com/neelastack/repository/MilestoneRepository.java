package com.neelastack.repository;

import com.neelastack.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByEngagementIdOrderByDisplayOrderAsc(UUID engagementId);
}
