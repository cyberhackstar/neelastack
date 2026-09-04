package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.util.List;

/**
 * The unlocked, full response for /api/v1/public/audit-preview/unlock — shown
 * on-page immediately (so the visitor gets instant value) and also emailed as a
 * PDF via ExecutiveReportPdfService, same as the estimator/architecture-review
 * flows. {@code inquiry.bookingUrl()} carries module 2's instant-booking trigger
 * for Tier-1 leads.
 */
@Builder
public record AuditUnlockResultDto(
        InquiryDto inquiry,
        int riskScore,
        String riskLevel,
        List<AuditFindingDto> findings,
        String disclaimer
) {}
