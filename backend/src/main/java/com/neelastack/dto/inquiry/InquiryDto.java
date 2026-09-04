package com.neelastack.dto.inquiry;

import com.neelastack.entity.InquiryIntent;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.entity.LeadTier;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InquiryDto(
        UUID id,
        String name,
        String email,
        String phone,
        String company,
        String projectType,
        String budgetRange,
        String message,
        InquiryStatus status,
        InquiryIntent intent,
        Integer leadScore,
        LeadTier leadTier,
        EstimateDto estimate,
        LocalDateTime createdAt,
        /** Non-null only for Tier-1 (HOT) leads with instant booking enabled/configured —
         *  see LeadScoringService and app.sales.calendly-url. Module 2 of the Client
         *  Acquisition & High-Ticket Conversion Engine: skip the "thank you" screen and
         *  drop a high-budget/urgent lead straight into a booking widget instead. */
        String bookingUrl
) {}
