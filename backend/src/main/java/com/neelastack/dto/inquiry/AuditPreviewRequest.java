package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Step 1 of the /audit-preview lead magnet (module 1 of the Client Acquisition &
 * High-Ticket Conversion Engine): no name/email/company here at all, deliberately —
 * this is the free, no-commitment half of the tool. See AuditUnlockRequest for the
 * gated step that actually creates a lead.
 */
public record AuditPreviewRequest(
        @NotEmpty List<@Size(max = 60) String> techStack,
        @NotEmpty List<@Size(max = 60) String> bottlenecks
) {}
