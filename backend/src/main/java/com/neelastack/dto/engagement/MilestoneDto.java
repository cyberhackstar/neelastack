package com.neelastack.dto.engagement;

import com.neelastack.entity.MilestoneStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record MilestoneDto(
        UUID id,
        UUID engagementId,
        String title,
        String description,
        MilestoneStatus status,
        LocalDate dueDate,
        Integer displayOrder
) {}
