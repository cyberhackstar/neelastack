package com.neelastack.dto.engagement;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record EngagementRequest(
        @NotBlank @Email String clientEmail,
        UUID inquiryId,
        @NotBlank String title,
        String description,
        LocalDate startDate,
        LocalDate targetEndDate
) {}
