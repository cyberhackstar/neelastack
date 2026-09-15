package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessAuditUnlockRequest(
        @NotBlank @Size(max = 60) String industry,
        @NotBlank @Size(max = 60) String websitePresence,
        @NotBlank @Size(max = 60) String customerAction,
        @NotBlank @Size(max = 60) String leadCapture,
        @NotBlank @Size(max = 60) String localDiscovery,
        @NotBlank @Size(max = 80) String primaryGoal,
        @Size(max = 300) String website,
        @Size(max = 80) String city,
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
