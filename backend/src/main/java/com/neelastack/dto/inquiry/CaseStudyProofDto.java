package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.util.List;

/**
 * The contextual social-proof block injected into a public quotation view (Client
 * Acquisition & High-Ticket Conversion Engine, module 3). Built only from a
 * published Project the admin has explicitly tagged with a matching service
 * category and, optionally, real metrics they've written themselves — see
 * Project#serviceCategories / Project#keyMetrics. Never fabricated, never a
 * generic fallback: if nothing matches, QuotationService omits this block
 * entirely rather than showing an unrelated or hollow case study.
 */
@Builder
public record CaseStudyProofDto(
        String title,
        String slug,
        String summary,
        String coverImageUrl,
        List<String> keyMetrics,
        Double averageRating,
        Integer reviewCount
) {}
