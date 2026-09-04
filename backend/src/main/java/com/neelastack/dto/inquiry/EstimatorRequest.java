package com.neelastack.dto.inquiry;

import com.neelastack.entity.InquiryIntent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for the multi-step project estimator (master prompt section 21). The wizard
 * itself lives entirely in the frontend — steps are answered one at a time there and
 * submitted together here, at the final "Preliminary estimate" step.
 */
public record EstimatorRequest(
        @NotNull InquiryIntent intent,
        @Size(max = 80) String projectType,
        @Size(max = 4000) String existingSystem,
        @Size(max = 4000) String scopeDetails,
        @Size(max = 60) String usersScale,
        List<@Size(max = 60) String> integrations,
        @Size(max = 60) String timeline,
        @Size(max = 40) String urgency,
        @Size(max = 60) String budgetRange,

        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @Size(max = 120) String company,

        @Size(max = 120) String utmSource,
        @Size(max = 120) String utmMedium,
        @Size(max = 120) String utmCampaign,
        @Size(max = 300) String referrer,
        @Size(max = 300) String landingPage
) {}
