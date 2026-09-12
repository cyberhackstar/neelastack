package com.neelastack.dto.engagement;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record EngagementRequest(
        @NotBlank @Email String clientEmail,
        // Only used when clientEmail has no existing account, to name the invited placeholder
        // user. If blank, EngagementService falls back to the linked inquiry's name, then to
        // an email-derived name — the client can always correct it when accepting the invite.
        String clientName,
        UUID inquiryId,
        @NotBlank String title,
        String description,
        LocalDate startDate,
        LocalDate targetEndDate
) {}
