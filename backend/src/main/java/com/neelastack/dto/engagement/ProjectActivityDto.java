package com.neelastack.dto.engagement;

import com.neelastack.entity.ProjectActivityType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ProjectActivityDto(
        UUID id,
        UUID engagementId,
        String actorName,
        String actorRole,
        ProjectActivityType activityType,
        String summary,
        LocalDateTime createdAt
) {}
