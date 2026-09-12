package com.neelastack.repository;

import com.neelastack.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") LocalDateTime now);
}
