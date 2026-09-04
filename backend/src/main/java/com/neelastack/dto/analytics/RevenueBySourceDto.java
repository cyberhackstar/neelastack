package com.neelastack.dto.analytics;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * One row of the revenue-by-source breakdown — inquiries grouped by their captured UTM
 * source (falling back to "Direct / Unknown" when no UTM params were present), with
 * funnel counts and won revenue rolled up per group. Source data is self-reported by the
 * browser (query params/referrer), same caveat as the underlying Inquiry.utmSource field.
 */
@Builder
public record RevenueBySourceDto(
        String source,
        long leadCount,
        long quotedCount,
        long wonCount,
        BigDecimal wonRevenue,
        /** wonCount / leadCount, 0-100. Null if leadCount is 0 (shouldn't happen, group only exists if it has leads). */
        Double conversionRatePercent
) {}
