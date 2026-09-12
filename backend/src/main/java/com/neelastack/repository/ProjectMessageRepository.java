package com.neelastack.repository;

import com.neelastack.entity.ProjectMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProjectMessageRepository extends JpaRepository<ProjectMessage, UUID> {

    List<ProjectMessage> findByEngagementIdOrderByCreatedAtAsc(UUID engagementId);

    // Unread = sent by someone else, after the viewer's last-read marker. Excluding the
    // viewer's own messages means sending a message never inflates your own unread badge.
    long countByEngagementIdAndSenderIdNotAndCreatedAtAfter(
            UUID engagementId, UUID senderId, LocalDateTime after);
}
