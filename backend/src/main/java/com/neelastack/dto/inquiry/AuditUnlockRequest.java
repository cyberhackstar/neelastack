package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Step 2 of the /audit-preview lead magnet: unlocking the full executive breakdown
 * requires name, email, and company — the moment this is submitted, a real Inquiry
 * is created (InquiryService#submitAuditPreview) and lead-scored like every other
 * entry point, per the master brief's "instantly feeding high-intent leads into
 * InquiryService" requirement.
 */
public record AuditUnlockRequest(
        @NotEmpty List<@Size(max = 60) String> techStack,
        @NotEmpty List<@Size(max = 60) String> bottlenecks,

        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @NotBlank @Size(max = 120) String company,

        @Size(max = 120) String utmSource,
        @Size(max = 120) String utmMedium,
        @Size(max = 120) String utmCampaign,
        @Size(max = 300) String referrer,
        @Size(max = 300) String landingPage
) {}
