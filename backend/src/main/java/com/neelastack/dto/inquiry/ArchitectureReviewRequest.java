package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for the "Already have an application?" free architecture-review lead magnet
 * (master prompt section 22) — deliberately lower-friction than the estimator: no
 * multi-step wizard, no budget/timeline questions, since the whole point is to lower the
 * bar to "yes, look at what I have."
 */
public record ArchitectureReviewRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @Size(max = 120) String company,
        @Size(max = 300) String applicationUrl,
        @NotBlank @Size(max = 4000) String currentStack,
        List<@Size(max = 60) String> primaryConcerns,
        @Size(max = 4000) String notes,

        @Size(max = 120) String utmSource,
        @Size(max = 120) String utmMedium,
        @Size(max = 120) String utmCampaign,
        @Size(max = 300) String referrer,
        @Size(max = 300) String landingPage
) {}
