package com.neelastack.dto.engagement;

import com.neelastack.entity.MilestoneApprovalAction;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MilestoneApprovalDto(
        UUID id,
        UUID milestoneId,
        UUID engagementId,
        MilestoneApprovalAction action,
        String comment,
        String actorName,
        LocalDateTime createdAt
) {}
