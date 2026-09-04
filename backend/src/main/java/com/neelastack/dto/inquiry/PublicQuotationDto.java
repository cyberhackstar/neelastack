package com.neelastack.dto.inquiry;

import com.neelastack.entity.QuotationStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What an unauthenticated client sees at the /quote/{token} link — no internal notes or IDs. */
@Builder
public record PublicQuotationDto(
        String title,
        String scopeSummary,
        List<QuotationLineItemDto> lineItems,
        BigDecimal totalAmount,
        String currency,
        QuotationStatus status,
        LocalDate validUntil,
        String clientName,
        /** Contextual proof point for the quoted service line — null when no
         *  published, matching case study exists. See CaseStudyProofDto. */
        CaseStudyProofDto relatedCaseStudy
) {}
