package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessAuditPreviewRequest(
        @NotBlank @Size(max = 60) String industry,
        @NotBlank @Size(max = 60) String websitePresence,
        @NotBlank @Size(max = 60) String customerAction,
        @NotBlank @Size(max = 60) String leadCapture,
        @NotBlank @Size(max = 60) String localDiscovery,
        @NotBlank @Size(max = 80) String primaryGoal
) {}
