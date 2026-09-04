package com.neelastack.dto.analytics;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Pipeline & revenue intelligence beyond the plain open/won snapshot in
 * AnalyticsSummaryDto — weighted pipeline, win rate, average deal size, and sales-cycle
 * duration, computed from quotation lifecycle timestamps (V19).
 */
@Builder
public record SalesIntelligenceDto(
        /** Sum of totalAmount across all SENT quotations — same as AnalyticsSummaryDto.openPipelineValue. */
        BigDecimal openPipelineValue,
        /** Sum of (totalAmount * stageProbability) across all SENT quotations — see QuotationStatus-based weighting note in AnalyticsService. */
        BigDecimal weightedPipelineValue,
        /** Sum of totalAmount across ACCEPTED quotations. */
        BigDecimal wonRevenue,
        /** ACCEPTED / (ACCEPTED + REJECTED) among quotations that received a response, 0-100. Null if none have been responded to yet. */
        Double winRatePercent,
        /** wonRevenue / count(ACCEPTED). Null if nothing has been won yet. */
        BigDecimal averageDealSize,
        /** Average days from sentAt to acceptedAt across ACCEPTED quotations. Null if none. */
        Double averageSalesCycleDays,
        /** Average hours from sentAt to firstViewedAt across quotations that have been opened. Null if none opened yet. */
        Double averageTimeToFirstViewHours,
        /** Count of SENT quotations that have never been opened. */
        long unviewedProposals,
        /** Count of SENT quotations opened at least once but not yet responded to. */
        long viewedAwaitingResponse
) {}
