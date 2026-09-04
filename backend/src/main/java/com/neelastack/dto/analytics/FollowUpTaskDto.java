package com.neelastack.dto.analytics;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single actionable follow-up surfaced by LeadFollowUpService — either an unviewed
 * proposal past the reminder threshold, or a viewed-but-unanswered one past the
 * escalation threshold. Rendered in the admin dashboard and in the daily digest email.
 */
@Builder
public record FollowUpTaskDto(
        UUID quotationId,
        UUID inquiryId,
        String clientName,
        String clientEmail,
        String quotationTitle,
        BigDecimal totalAmount,
        FollowUpReason reason,
        LocalDateTime sentAt,
        LocalDateTime lastViewedAt,
        long daysSinceLastActivity
) {
    public enum FollowUpReason {
        /** Sent 3+ days ago, never opened. */
        UNVIEWED_REMINDER,
        /** Opened at least once, 2+ days since the last open, still no response. */
        VIEWED_NO_RESPONSE
    }
}
