package com.neelastack.dto.engagement;

import com.neelastack.entity.EngagementStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record EngagementDto(
        UUID id,
        UUID clientId,
        String clientName,
        String clientEmail,
        String title,
        String description,
        EngagementStatus status,
        LocalDate startDate,
        LocalDate targetEndDate,
        LocalDateTime createdAt
) {}
