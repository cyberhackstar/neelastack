package com.neelastack.service;

import com.neelastack.dto.analytics.AnalyticsSummaryDto;
import com.neelastack.dto.analytics.AttributionBreakdownDto;
import com.neelastack.dto.analytics.AttributionDimension;
import com.neelastack.dto.analytics.FollowUpTaskDto;
import com.neelastack.dto.analytics.RevenueBySourceDto;
import com.neelastack.dto.analytics.SalesIntelligenceDto;
import com.neelastack.dto.inquiry.InquiryDto;
import com.neelastack.entity.FollowUpDismissal;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.entity.LeadTier;
import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationStatus;
import com.neelastack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final InquiryRepository inquiryRepository;
    private final EngagementRepository engagementRepository;
    private final InvoiceRepository invoiceRepository;
    private final BlogPostRepository blogPostRepository;
    private final ProjectRepository projectRepository;
    private final QuotationRepository quotationRepository;
    private final FollowUpDismissalRepository followUpDismissalRepository;

    /**
     * Stage-based win probability for the weighted-pipeline calculation. Deliberately a
     * static heuristic keyed off QuotationStatus rather than a new per-quotation DB
     * column — a solo/small practice doesn't have enough historical volume yet for a
     * real per-deal probability model, and a hardcoded stage weight is honest about
     * that (it's a rough steer for prioritization, not a revenue forecast commitment).
     * Revisit with real win-rate-by-stage data once there's enough volume to trust it.
     */
    private static final BigDecimal SENT_STAGE_PROBABILITY = new BigDecimal("0.40");

    @Value("${app.followups.unviewed-reminder-days:3}")
    private int unviewedReminderDays;

    @Value("${app.followups.viewed-no-response-days:2}")
    private int viewedNoResponseDays;

    public AnalyticsSummaryDto summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        engagementRepository.countByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        List<InquiryDto> recent = inquiryRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(i -> InquiryDto.builder()
                        .id(i.getId())
                        .name(i.getName())
                        .email(i.getEmail())
                        .phone(i.getPhone())
                        .company(i.getCompany())
                        .projectType(i.getProjectType())
                        .budgetRange(i.getBudgetRange())
                        .message(i.getMessage())
                        .status(i.getStatus())
                        .intent(i.getIntent())
                        .leadScore(i.getLeadScore())
                        .leadTier(i.getLeadTier())
                        .createdAt(i.getCreatedAt())
                        .build())
                .getContent();

        return AnalyticsSummaryDto.builder()
                .totalInquiries(inquiryRepository.count())
                .newInquiries(inquiryRepository.countByStatus(InquiryStatus.NEW))
                .totalEngagements(engagementRepository.count())
                .engagementsByStatus(byStatus)
                .totalRevenueCollected(invoiceRepository.sumPaidAmount())
                .pendingInvoiceAmount(invoiceRepository.sumPendingAmount())
                .totalBlogPosts(blogPostRepository.count())
                .totalProjects(projectRepository.count())
                .hotLeads(inquiryRepository.countByLeadTier(LeadTier.HOT))
                .openPipelineValue(sumQuotationAmounts(QuotationStatus.SENT))
                .wonPipelineValue(sumQuotationAmounts(QuotationStatus.ACCEPTED))
                .recentInquiries(recent)
                .build();
    }

    /**
     * Sums quotation amounts by status — a lightweight pipeline snapshot (master prompt
     * section 52), not a full weighted-pipeline/win-rate/sales-cycle dashboard. Fetching
     * and summing in Java rather than a DB-side SUM query because quotation volume for a
     * solo/small practice is small (tens, not thousands) — revisit with a real aggregate
     * query if that stops being true.
     */
    private BigDecimal sumQuotationAmounts(QuotationStatus status) {
        return quotationRepository.findByStatus(status).stream()
                .map(q -> q.getTotalAmount() == null ? BigDecimal.ZERO : q.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Weighted pipeline, win rate, average deal size, sales-cycle duration, and
     * proposal-response timing (master prompt section 2 — "granular proposal lifecycle
     * metrics" / "weighted pipeline & revenue intelligence"). Reads the full SENT/
     * ACCEPTED/REJECTED sets rather than DB-side aggregates, matching the existing
     * sumQuotationAmounts approach above — fine at this practice's volume (tens, not
     * thousands of quotations); revisit with real aggregate queries if that changes.
     */
    public SalesIntelligenceDto salesIntelligence() {
        List<Quotation> sent = quotationRepository.findByStatus(QuotationStatus.SENT);
        List<Quotation> accepted = quotationRepository.findByStatus(QuotationStatus.ACCEPTED);
        List<Quotation> rejected = quotationRepository.findByStatus(QuotationStatus.REJECTED);

        BigDecimal openPipeline = sumAmounts(sent);
        BigDecimal weightedPipeline = openPipeline.multiply(SENT_STAGE_PROBABILITY)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal wonRevenue = sumAmounts(accepted);

        int respondedCount = accepted.size() + rejected.size();
        Double winRate = respondedCount == 0 ? null
                : BigDecimal.valueOf(accepted.size())
                        .divide(BigDecimal.valueOf(respondedCount), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

        BigDecimal avgDealSize = accepted.isEmpty() ? null
                : wonRevenue.divide(BigDecimal.valueOf(accepted.size()), 2, RoundingMode.HALF_UP);

        Double avgSalesCycleDays = averageOf(accepted.stream()
                .filter(q -> q.getSentAt() != null && q.getAcceptedAt() != null)
                .map(q -> Duration.between(q.getSentAt(), q.getAcceptedAt()).toHours() / 24.0)
                .toList());

        List<Quotation> everSent = new ArrayList<>(sent);
        everSent.addAll(accepted);
        everSent.addAll(rejected);
        Double avgTimeToFirstViewHours = averageOf(everSent.stream()
                .filter(q -> q.getSentAt() != null && q.getFirstViewedAt() != null)
                .map(q -> (double) Duration.between(q.getSentAt(), q.getFirstViewedAt()).toHours())
                .toList());

        long unviewed = sent.stream().filter(q -> q.getViewCount() == null || q.getViewCount() == 0).count();
        long viewedAwaitingResponse = sent.stream().filter(q -> q.getViewCount() != null && q.getViewCount() > 0).count();

        return SalesIntelligenceDto.builder()
                .openPipelineValue(openPipeline)
                .weightedPipelineValue(weightedPipeline)
                .wonRevenue(wonRevenue)
                .winRatePercent(winRate)
                .averageDealSize(avgDealSize)
                .averageSalesCycleDays(avgSalesCycleDays)
                .averageTimeToFirstViewHours(avgTimeToFirstViewHours)
                .unviewedProposals(unviewed)
                .viewedAwaitingResponse(viewedAwaitingResponse)
                .build();
    }

    /**
     * Revenue and conversion grouped by captured UTM source (master prompt section 2 —
     * "attribution & revenue-by-source breakdown"). Groups inquiries by utmSource
     * (falling back to "Direct / Unknown"), then rolls up each group's quotations for
     * quoted/won counts and won revenue.
     */
    public List<RevenueBySourceDto> revenueBySource() {
        return revenueByAttribution(AttributionDimension.SOURCE).stream()
                .map(row -> RevenueBySourceDto.builder()
                        .source(row.value())
                        .leadCount(row.leadCount())
                        .quotedCount(row.quotedCount())
                        .wonCount(row.wonCount())
                        .wonRevenue(row.wonRevenue())
                        .conversionRatePercent(row.conversionRatePercent())
                        .build())
                .toList();
    }

    /**
     * Generalizes revenueBySource() to any of the four attribution fields captured on
     * Inquiry (utmSource, utmMedium, utmCampaign, landingPage) — master prompt Section 1,
     * "Source, Medium, Campaign, Landing Page" attribution table. Same funnel-rollup
     * logic as before, just keyed off a caller-selected field instead of always utmSource.
     */
    public List<AttributionBreakdownDto> revenueByAttribution(AttributionDimension dimension) {
        List<Inquiry> inquiries = inquiryRepository.findAll();
        List<Quotation> allQuotations = quotationRepository.findAll();

        Map<UUID, List<Quotation>> quotationsByInquiry = new LinkedHashMap<>();
        for (Quotation q : allQuotations) {
            if (q.getInquiry() == null) continue;
            quotationsByInquiry.computeIfAbsent(q.getInquiry().getId(), k -> new ArrayList<>()).add(q);
        }

        Map<String, List<Inquiry>> inquiriesByGroup = new LinkedHashMap<>();
        for (Inquiry inquiry : inquiries) {
            String raw = switch (dimension) {
                case SOURCE -> inquiry.getUtmSource();
                case MEDIUM -> inquiry.getUtmMedium();
                case CAMPAIGN -> inquiry.getUtmCampaign();
                case LANDING_PAGE -> inquiry.getLandingPage();
            };
            String group = (raw == null || raw.isBlank()) ? "Direct / Unknown" : raw;
            inquiriesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(inquiry);
        }

        List<AttributionBreakdownDto> rows = new ArrayList<>();
        for (Map.Entry<String, List<Inquiry>> entry : inquiriesByGroup.entrySet()) {
            long leadCount = entry.getValue().size();
            long quotedCount = 0;
            long wonCount = 0;
            BigDecimal wonRevenue = BigDecimal.ZERO;

            for (Inquiry inquiry : entry.getValue()) {
                List<Quotation> quotations = quotationsByInquiry.getOrDefault(inquiry.getId(), List.of());
                if (!quotations.isEmpty()) quotedCount++;
                for (Quotation q : quotations) {
                    if (q.getStatus() == QuotationStatus.ACCEPTED) {
                        wonCount++;
                        wonRevenue = wonRevenue.add(q.getTotalAmount() == null ? BigDecimal.ZERO : q.getTotalAmount());
                    }
                }
            }

            Double conversionRate = leadCount == 0 ? null
                    : BigDecimal.valueOf(wonCount)
                            .divide(BigDecimal.valueOf(leadCount), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

            rows.add(AttributionBreakdownDto.builder()
                    .dimension(dimension)
                    .value(entry.getKey())
                    .leadCount(leadCount)
                    .quotedCount(quotedCount)
                    .wonCount(wonCount)
                    .wonRevenue(wonRevenue)
                    .conversionRatePercent(conversionRate)
                    .build());
        }

        rows.sort(Comparator.comparing(AttributionBreakdownDto::wonRevenue).reversed());
        return rows;
    }

    /**
     * Follow-up candidates for the automated lead follow-up system (master prompt
     * section 2): SENT quotations either never opened past the reminder threshold, or
     * opened but unanswered past the escalation threshold. Shared by
     * AdminAnalyticsController (on-demand dashboard view) and LeadFollowUpService (daily
     * digest email) so both surfaces agree on exactly the same candidate set.
     */
    public List<FollowUpTaskDto> followUpTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime unviewedCutoff = now.minusDays(unviewedReminderDays);
        LocalDateTime viewedCutoff = now.minusDays(viewedNoResponseDays);

        // Currently-active deferrals: dismissed indefinitely (dismissedUntil == null),
        // or snoozed and the snooze hasn't expired yet. An expired snooze is left in
        // the table (it's just one row per quotation, cheap to keep) but no longer
        // filters anything -- the task reappears on its own once dismissedUntil passes.
        Map<UUID, FollowUpDismissal> activeDismissals = new LinkedHashMap<>();
        for (FollowUpDismissal d : followUpDismissalRepository.findAll()) {
            if (d.getDismissedUntil() == null || d.getDismissedUntil().isAfter(now)) {
                activeDismissals.put(d.getQuotationId(), d);
            }
        }

        List<FollowUpTaskDto> tasks = new ArrayList<>();

        quotationRepository.findByStatusAndViewCountAndSentAtBefore(QuotationStatus.SENT, 0, unviewedCutoff)
                .stream()
                .filter(q -> !activeDismissals.containsKey(q.getId()))
                .forEach(q -> tasks.add(toFollowUpTask(q, FollowUpTaskDto.FollowUpReason.UNVIEWED_REMINDER,
                        q.getSentAt())));

        quotationRepository.findByStatusAndViewCountGreaterThanAndLastViewedAtBeforeOrderByLastViewedAtAsc(
                        QuotationStatus.SENT, 0, viewedCutoff)
                .stream()
                .filter(q -> !activeDismissals.containsKey(q.getId()))
                .forEach(q -> tasks.add(toFollowUpTask(q, FollowUpTaskDto.FollowUpReason.VIEWED_NO_RESPONSE,
                        q.getLastViewedAt())));

        return tasks;
    }

    /** Marks a follow-up as done indefinitely -- filtered out until quotation status itself changes. */
    public void dismissFollowUp(UUID quotationId, String dismissedBy, String reason) {
        upsertDismissal(quotationId, dismissedBy, null, reason);
    }

    /** Defers a follow-up until a specific time, after which it reappears on its own. */
    public void snoozeFollowUp(UUID quotationId, String dismissedBy, LocalDateTime until, String reason) {
        upsertDismissal(quotationId, dismissedBy, until, reason);
    }

    private void upsertDismissal(UUID quotationId, String dismissedBy, LocalDateTime until, String reason) {
        FollowUpDismissal dismissal = followUpDismissalRepository.findByQuotationId(quotationId)
                .orElseGet(() -> FollowUpDismissal.builder().quotationId(quotationId).build());
        dismissal.setDismissedBy(dismissedBy);
        dismissal.setDismissedUntil(until);
        dismissal.setReason(reason);
        followUpDismissalRepository.save(dismissal);
    }

    private FollowUpTaskDto toFollowUpTask(Quotation q, FollowUpTaskDto.FollowUpReason reason, LocalDateTime sinceReference) {
        long daysSince = sinceReference == null ? 0
                : Duration.between(sinceReference, LocalDateTime.now()).toDays();
        return FollowUpTaskDto.builder()
                .quotationId(q.getId())
                .inquiryId(q.getInquiry() != null ? q.getInquiry().getId() : null)
                .clientName(q.getInquiry() != null ? q.getInquiry().getName() : null)
                .clientEmail(q.getInquiry() != null ? q.getInquiry().getEmail() : null)
                .quotationTitle(q.getTitle())
                .totalAmount(q.getTotalAmount())
                .reason(reason)
                .sentAt(q.getSentAt())
                .lastViewedAt(q.getLastViewedAt())
                .daysSinceLastActivity(daysSince)
                .build();
    }

    private BigDecimal sumAmounts(List<Quotation> quotations) {
        return quotations.stream()
                .map(q -> q.getTotalAmount() == null ? BigDecimal.ZERO : q.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Double averageOf(List<Double> values) {
        if (values.isEmpty()) return null;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
