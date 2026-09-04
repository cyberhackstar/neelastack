package com.neelastack.repository;

import com.neelastack.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, UUID> {
    List<ProjectFile> findByEngagementIdOrderByCreatedAtDesc(UUID engagementId);
}
