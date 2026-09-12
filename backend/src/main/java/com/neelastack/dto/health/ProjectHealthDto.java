package com.neelastack.dto.health;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ProjectHealthDto(
        UUID engagementId,
        ProjectHealthStatus status,
        List<String> reasons
) {}
