package com.neelastack.dto.engagement;

import jakarta.validation.constraints.NotBlank;

public record RequestMilestoneChangesRequest(
        @NotBlank String comment
) {}
