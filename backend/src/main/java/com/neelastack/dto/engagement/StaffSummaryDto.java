package com.neelastack.dto.engagement;

import lombok.Builder;

import java.util.UUID;

@Builder
public record StaffSummaryDto(
        UUID id,
        String fullName,
        String email
) {}
