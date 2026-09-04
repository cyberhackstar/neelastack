package com.neelastack.dto.engagement;

import com.neelastack.entity.MilestoneStatus;
import jakarta.validation.constraints.NotNull;

public record MilestoneStatusUpdateRequest(
        @NotNull MilestoneStatus status
) {}
