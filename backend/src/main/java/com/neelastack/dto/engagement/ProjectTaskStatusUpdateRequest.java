package com.neelastack.dto.engagement;

import com.neelastack.entity.ProjectTaskStatus;
import jakarta.validation.constraints.NotNull;

public record ProjectTaskStatusUpdateRequest(
        @NotNull ProjectTaskStatus status
) {}
