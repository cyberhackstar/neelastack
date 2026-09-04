package com.neelastack.dto.analytics;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * One row of the revenue-attribution breakdown, grouped by whichever
 * {@link AttributionDimension} was requested (utmSource / utmMedium / utmCampaign /
 * landingPage). Generalizes {@link RevenueBySourceDto} to the other three attribution
 * fields that already exist on {@code Inquiry} but weren't previously broken out.
 * Source data is self-reported by the browser (query params/referrer), same caveat as
 * the underlying Inquiry fields.
 */
@Builder
public record AttributionBreakdownDto(
        AttributionDimension dimension,
        /** The group value for this dimension, e.g. "google" for SOURCE, or "Direct / Unknown" if not captured. */
        String value,
        long leadCount,
        long quotedCount,
        long wonCount,
        BigDecimal wonRevenue,
        /** wonCount / leadCount, 0-100. Null if leadCount is 0 (shouldn't happen, group only exists if it has leads). */
        Double conversionRatePercent
) {}
