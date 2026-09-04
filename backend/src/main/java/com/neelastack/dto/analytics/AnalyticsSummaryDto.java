package com.neelastack.dto.analytics;

import com.neelastack.dto.inquiry.InquiryDto;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Builder
public record AnalyticsSummaryDto(
        long totalInquiries,
        long newInquiries,
        long totalEngagements,
        Map<String, Long> engagementsByStatus,
        BigDecimal totalRevenueCollected,
        BigDecimal pendingInvoiceAmount,
        long totalBlogPosts,
        long totalProjects,
        long hotLeads,
        BigDecimal openPipelineValue,
        BigDecimal wonPipelineValue,
        List<InquiryDto> recentInquiries
) {}
