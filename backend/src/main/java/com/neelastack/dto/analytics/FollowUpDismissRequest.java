package com.neelastack.dto.analytics;

/** Optional body for {@code POST /admin/analytics/follow-ups/{quotationId}/dismiss}. */
public record FollowUpDismissRequest(String reason) {}
