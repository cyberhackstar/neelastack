package com.neelastack.controller;

import com.neelastack.dto.analytics.AnalyticsSummaryDto;
import com.neelastack.dto.analytics.AttributionBreakdownDto;
import com.neelastack.dto.analytics.AttributionDimension;
import com.neelastack.dto.analytics.FollowUpDismissRequest;
import com.neelastack.dto.analytics.FollowUpSnoozeRequest;
import com.neelastack.dto.analytics.FollowUpTaskDto;
import com.neelastack.dto.analytics.RevenueBySourceDto;
import com.neelastack.dto.analytics.SalesIntelligenceDto;
import com.neelastack.service.AnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin — analytics", description = "Requires ROLE_ADMIN")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryDto summary() {
        return analyticsService.summary();
    }

    /** Weighted pipeline, win rate, average deal size, sales-cycle & response-time metrics. */
    @GetMapping("/sales-intelligence")
    public SalesIntelligenceDto salesIntelligence() {
        return analyticsService.salesIntelligence();
    }

    /** Leads, quotes, wins, and won revenue grouped by captured UTM source. */
    @GetMapping("/revenue-by-source")
    public List<RevenueBySourceDto> revenueBySource() {
        return analyticsService.revenueBySource();
    }

    /** Same breakdown as revenue-by-source, generalized to source/medium/campaign/landing-page. */
    @GetMapping("/revenue-by-attribution")
    public List<AttributionBreakdownDto> revenueByAttribution(
            @RequestParam(defaultValue = "SOURCE") AttributionDimension dimension) {
        return analyticsService.revenueByAttribution(dimension);
    }

    /** On-demand view of the same follow-up candidates the daily digest email sends. */
    @GetMapping("/follow-ups")
    public List<FollowUpTaskDto> followUps() {
        return analyticsService.followUpTasks();
    }

    /** Marks a follow-up as done; it stays hidden until the quotation's status itself changes. */
    @PostMapping("/follow-ups/{quotationId}/dismiss")
    public ResponseEntity<Void> dismissFollowUp(
            @PathVariable UUID quotationId,
            @RequestBody(required = false) FollowUpDismissRequest request,
            Authentication authentication) {
        String reason = request == null ? null : request.reason();
        analyticsService.dismissFollowUp(quotationId, authentication.getName(), reason);
        return ResponseEntity.noContent().build();
    }

    /** Defers a follow-up until a given time; it reappears on its own after that. */
    @PostMapping("/follow-ups/{quotationId}/snooze")
    public ResponseEntity<Void> snoozeFollowUp(
            @PathVariable UUID quotationId,
            @Valid @RequestBody FollowUpSnoozeRequest request,
            Authentication authentication) {
        analyticsService.snoozeFollowUp(quotationId, authentication.getName(), request.until(), request.reason());
        return ResponseEntity.noContent().build();
    }
}
