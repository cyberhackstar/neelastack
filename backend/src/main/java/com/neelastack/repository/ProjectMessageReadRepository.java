package com.neelastack.repository;

import com.neelastack.entity.ProjectMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMessageReadRepository extends JpaRepository<ProjectMessageRead, UUID> {

    Optional<ProjectMessageRead> findByEngagementIdAndUserId(UUID engagementId, UUID userId);
}
