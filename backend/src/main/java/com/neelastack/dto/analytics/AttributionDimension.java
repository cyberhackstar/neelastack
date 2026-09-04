package com.neelastack.dto.analytics;

/**
 * Which captured field on {@code Inquiry} to group the revenue-attribution breakdown
 * by. All four fields already exist on {@code Inquiry} (utmSource, utmMedium,
 * utmCampaign, landingPage) -- this only controls how AnalyticsService#revenueByAttribution
 * groups rows, it doesn't require any new data capture.
 */
public enum AttributionDimension {
    SOURCE,
    MEDIUM,
    CAMPAIGN,
    LANDING_PAGE
}
