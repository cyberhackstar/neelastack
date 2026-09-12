package com.neelastack.repository;

import com.neelastack.entity.ChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    // DESC — newest first, matching the review's own "CHANGE REQUEST #12" style single feed
    // rather than sorting by status or priority.
    List<ChangeRequest> findByEngagementIdOrderByCreatedAtDesc(UUID engagementId);
}
