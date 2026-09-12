package com.neelastack.dto.notification;

import com.neelastack.entity.NotificationPriority;
import com.neelastack.entity.NotificationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record NotificationDto(
        UUID id,
        UUID engagementId,
        NotificationType type,
        NotificationPriority priority,
        String title,
        String body,
        String relatedEntityType,
        UUID relatedEntityId,
        String deepLink,
        boolean read,
        LocalDateTime createdAt
) {}
