package com.neelastack.dto.engagement;

import com.neelastack.entity.ChangeRequestPriority;
import com.neelastack.entity.ChangeRequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ChangeRequestDto(
        UUID id,
        UUID engagementId,
        String requestedByName,
        String title,
        String description,
        ChangeRequestPriority priority,
        ChangeRequestStatus status,
        BigDecimal estimatedCost,
        String estimatedCostCurrency,
        Integer estimatedTimelineDays,
        ProjectFileDto attachment,
        LocalDateTime createdAt
) {}
