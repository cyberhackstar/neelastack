package com.neelastack.repository;

import com.neelastack.entity.ProjectActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, UUID> {

    // DESC — the feed reads newest-first, same order as the mockup in the review (today's
    // events at the top, working back through the project's history).
    List<ProjectActivity> findByEngagementIdOrderByCreatedAtDesc(UUID engagementId);
}
