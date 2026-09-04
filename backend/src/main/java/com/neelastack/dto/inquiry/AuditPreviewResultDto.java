package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.util.List;

/**
 * Free teaser result for /api/v1/public/audit-preview/score — enough real signal
 * (an actual computed score, two real findings) to earn the gate, with the rest
 * locked behind AuditUnlockRequest. Nothing here is persisted; recomputed fresh on
 * every call from exactly what the visitor selected.
 */
@Builder
public record AuditPreviewResultDto(
        int riskScore,
        String riskLevel,
        List<String> teaserFindings,
        int lockedFindingsCount,
        String disclaimer
) {}
