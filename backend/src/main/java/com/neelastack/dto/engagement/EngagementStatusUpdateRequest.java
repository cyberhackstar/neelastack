package com.neelastack.dto.engagement;

import com.neelastack.entity.EngagementStatus;
import jakarta.validation.constraints.NotNull;

public record EngagementStatusUpdateRequest(
        @NotNull EngagementStatus status
) {}
