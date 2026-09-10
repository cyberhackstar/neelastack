package com.neelastack.dto.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record BookingRequest(
        @NotBlank String meetingTypeSlug,
        @NotNull OffsetDateTime startAt,
        @NotBlank @Size(max = 60) String clientTimezone,
        @NotBlank @Size(max = 160) String clientName,
        @NotBlank @Email @Size(max = 180) String clientEmail,
        @Size(max = 30) String clientPhone,
        @Size(max = 160) String clientCompany,

        /** Free-form answers keyed by the meeting type's configured form-field keys. */
        Map<String, String> formResponses,

        /** Optional — links this booking back to an existing inquiry (e.g. the HOT-lead
         *  instant-booking flow from InquiryService). */
        UUID inquiryId,

        /** Client-generated key so a retried submit (double-click, flaky network) never
         *  creates a second booking. */
        @Size(max = 80) String idempotencyKey,

        @Size(max = 60) String source,
        @Size(max = 120) String utmSource,
        @Size(max = 120) String utmMedium,
        @Size(max = 120) String utmCampaign,
        @Size(max = 300) String landingPage,
        @Size(max = 300) String referrer
) {}
