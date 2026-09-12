package com.neelastack.dto.engagement;

import com.neelastack.entity.ProjectTaskStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ProjectTaskDto(
        UUID id,
        UUID milestoneId,
        UUID engagementId,
        String title,
        String description,
        ProjectTaskStatus status,
        LocalDate dueDate,
        boolean clientActionRequired,
        String assigneeName,
        Integer displayOrder,
        LocalDateTime createdAt
) {}
