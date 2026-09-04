package com.neelastack.service;

import com.neelastack.dto.inquiry.ArchitectureReviewRequest;
import com.neelastack.dto.inquiry.AuditFindingDto;
import com.neelastack.dto.inquiry.AuditUnlockRequest;
import com.neelastack.dto.inquiry.AuditUnlockResultDto;
import com.neelastack.dto.inquiry.EstimateDto;
import com.neelastack.dto.inquiry.EstimatorRequest;
import com.neelastack.dto.inquiry.EstimatorResponseDto;
import com.neelastack.dto.inquiry.InquiryDto;
import com.neelastack.dto.inquiry.InquiryRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryIntent;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InquiryService {

    private static final String GENERIC_ESTIMATE_DISCLAIMER =
            "Preliminary estimate — not a binding quotation.";

    private final InquiryRepository inquiryRepository;
    private final EmailService emailService;
    private final LeadScoringService leadScoringService;
    private final EstimateCalculatorService estimateCalculatorService;
    private final ExecutiveReportPdfService executiveReportPdfService;
    private final AuditLogService auditLogService;
    private final ArchitectureRiskScoringService architectureRiskScoringService;

    @org.springframework.beans.factory.annotation.Value("${app.sales.booking-enabled:false}")
    private boolean bookingEnabled;

    @org.springframework.beans.factory.annotation.Value("${app.sales.calendly-url:}")
    private String calendlyUrl;

    @Transactional
    public InquiryDto submit(InquiryRequest request) {
        int score = leadScoringService.score(
                request.budgetRange(), null, null, null, null, request.projectType());

        Inquiry inquiry = Inquiry.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .company(request.company())
                .projectType(request.projectType())
                .budgetRange(request.budgetRange())
                .message(request.message())
                .status(InquiryStatus.NEW)
                .source("website")
                .intent(InquiryIntent.GENERAL)
                .leadScore(score)
                .leadTier(leadScoringService.tier(score))
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);

        emailService.sendInquiryConfirmation(saved);
        emailService.sendAdminNewInquiryAlert(saved);

        return toDto(saved);
    }

    @Transactional
    public EstimatorResponseDto submitEstimator(EstimatorRequest request) {
        EstimateDto estimate = estimateCalculatorService.calculate(
                request.intent(), request.projectType(), request.existingSystem(),
                request.integrations(), request.timeline(), request.usersScale(), request.urgency());

        int score = leadScoringService.score(
                request.budgetRange(), request.timeline(), request.urgency(),
                request.existingSystem(), request.integrations(), request.projectType());

        String integrationsText = request.integrations() == null || request.integrations().isEmpty()
                ? null
                : String.join(", ", request.integrations());

        Inquiry inquiry = Inquiry.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .company(request.company())
                .projectType(request.projectType())
                .budgetRange(request.budgetRange())
                .message(buildSummaryMessage(request, integrationsText))
                .status(InquiryStatus.NEW)
                .source("estimator")
                .intent(request.intent())
                .existingSystem(request.existingSystem())
                .scopeDetails(request.scopeDetails())
                .usersScale(request.usersScale())
                .integrations(integrationsText)
                .timeline(request.timeline())
                .urgency(request.urgency())
                .estimateLow(estimate.low())
                .estimateHigh(estimate.high())
                .estimateCurrency(estimate.currency())
                .leadScore(score)
                .leadTier(leadScoringService.tier(score))
                .utmSource(request.utmSource())
                .utmMedium(request.utmMedium())
                .utmCampaign(request.utmCampaign())
                .referrer(request.referrer())
                .landingPage(request.landingPage())
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);

        emailService.sendInquiryConfirmation(saved);
        emailService.sendAdminNewInquiryAlert(saved);
        sendExecutiveReportSafely(saved);

        return EstimatorResponseDto.builder()
                .inquiry(toDto(saved))
                .estimate(estimate)
                .build();
    }

    @Transactional
    public InquiryDto submitArchitectureReview(ArchitectureReviewRequest request) {
        // No estimate calculated here — a review is a free lead magnet, not a priced
        // engagement, so there's nothing to put a range on yet.
        int score = leadScoringService.score(
                null, null, null, request.currentStack(), null, null);

        Inquiry inquiry = Inquiry.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .company(request.company())
                .projectType("Architecture review")
                .message(buildArchitectureReviewSummary(request))
                .status(InquiryStatus.NEW)
                .source("architecture_review")
                .intent(InquiryIntent.AUDIT)
                .existingSystem(request.currentStack())
                .scopeDetails(request.notes())
                .integrations(request.primaryConcerns() == null || request.primaryConcerns().isEmpty()
                        ? null : String.join(", ", request.primaryConcerns()))
                .leadScore(score)
                .leadTier(leadScoringService.tier(score))
                .utmSource(request.utmSource())
                .utmMedium(request.utmMedium())
                .utmCampaign(request.utmCampaign())
                .referrer(request.referrer())
                .landingPage(request.landingPage())
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);

        emailService.sendInquiryConfirmation(saved);
        emailService.sendAdminNewInquiryAlert(saved);
        sendExecutiveReportSafely(saved);

        return toDto(saved);
    }

    /**
     * Module 1 of the Client Acquisition & High-Ticket Conversion Engine: the gated
     * unlock step of the /audit-preview lead magnet. Computes the same deterministic
     * risk score/findings as the free preview (ArchitectureRiskScoringService), but
     * this call is what actually creates the Inquiry — the free preview step never
     * touches the database.
     */
    @Transactional
    public AuditUnlockResultDto submitAuditPreview(AuditUnlockRequest request) {
        ArchitectureRiskScoringService.FullReport report =
                architectureRiskScoringService.fullReport(request.techStack(), request.bottlenecks());

        int score = leadScoringService.score(
                null, null, null, null, request.techStack(), null);
        // The risk score itself is also a strong buying signal independent of the
        // generic lead-scoring heuristics above (a visitor who flagged a CRITICAL
        // payment-concurrency risk on their own stack is a warmer lead than the
        // generic weights alone would suggest) — fold a bounded bonus in directly.
        score = Math.min(100, score + (report.riskScore() >= 70 ? 20 : report.riskScore() >= 45 ? 10 : 0));

        Inquiry inquiry = Inquiry.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .company(request.company())
                .projectType("Architecture risk audit")
                .message(buildAuditPreviewSummary(request, report))
                .status(InquiryStatus.NEW)
                .source("audit_preview")
                .intent(InquiryIntent.AUDIT)
                .existingSystem(String.join(", ", request.techStack()))
                .integrations(String.join(", ", request.bottlenecks()))
                .leadScore(score)
                .leadTier(leadScoringService.tier(score))
                .utmSource(request.utmSource())
                .utmMedium(request.utmMedium())
                .utmCampaign(request.utmCampaign())
                .referrer(request.referrer())
                .landingPage(request.landingPage())
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);

        emailService.sendInquiryConfirmation(saved);
        emailService.sendAdminNewInquiryAlert(saved);
        sendExecutiveReportSafely(saved);

        List<AuditFindingDto> findings = report.findings().stream()
                .map(f -> AuditFindingDto.builder()
                        .title(f.title())
                        .severity(f.severity())
                        .summary(f.summary())
                        .recommendation(f.recommendation())
                        .build())
                .toList();

        return AuditUnlockResultDto.builder()
                .inquiry(toDto(saved))
                .riskScore(report.riskScore())
                .riskLevel(report.riskLevel())
                .findings(findings)
                .disclaimer("A self-assessment score based on the stack and concerns you selected — not an "
                        + "automated scan of your actual code. Book a free architecture review for that.")
                .build();
    }

    private String buildAuditPreviewSummary(AuditUnlockRequest request, ArchitectureRiskScoringService.FullReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Instant architecture risk score: ").append(report.riskScore())
                .append(" (").append(report.riskLevel()).append(")\n\n");
        sb.append("Stack: ").append(String.join(", ", request.techStack())).append("\n");
        sb.append("Flagged concerns: ").append(String.join(", ", request.bottlenecks())).append("\n");
        sb.append("Submitted via the /audit-preview lead magnet.");
        return sb.toString();
    }

    /**
     * Generates and emails the executive PDF brief for Estimator/Architecture Review leads.
     * Generation runs synchronously (it's fast, in-memory, and needs the saved entity), but
     * the send itself is {@code @Async} inside {@link EmailService} — either way, a PDF or
     * SMTP failure here must never fail the inquiry submission that triggered it.
     */
    private void sendExecutiveReportSafely(Inquiry inquiry) {
        try {
            byte[] pdf = executiveReportPdfService.generate(inquiry);
            String fileName = "neelastack-executive-brief-" + inquiry.getId() + ".pdf";
            emailService.sendExecutiveReport(inquiry, pdf, fileName);
        } catch (Exception ex) {
            log.error("Failed to generate/send executive report for inquiry {}: {}", inquiry.getId(), ex.getMessage());
        }
    }

    private String buildArchitectureReviewSummary(ArchitectureReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Architecture review request\n\n");
        if (request.applicationUrl() != null && !request.applicationUrl().isBlank()) {
            sb.append("Application: ").append(request.applicationUrl()).append("\n");
        }
        sb.append("Current stack: ").append(request.currentStack()).append("\n");
        if (request.primaryConcerns() != null && !request.primaryConcerns().isEmpty()) {
            sb.append("Primary concerns: ").append(String.join(", ", request.primaryConcerns())).append("\n");
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("Notes: ").append(request.notes()).append("\n");
        }
        sb.append("Submitted via the free architecture review form.");
        return sb.toString();
    }

    public Page<InquiryDto> list(Pageable pageable) {
        return inquiryRepository.findAll(pageable).map(this::toDto);
    }

    public InquiryDto get(UUID id) {
        return inquiryRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + id));
    }

    /** On-demand regeneration for the admin dashboard — e.g. to re-download a copy of a
     *  brief already emailed, or to preview one for an inquiry submitted before this
     *  feature shipped (older rows still render fine; sections with no data just omit). */
    public byte[] generateExecutiveReportPdf(UUID id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + id));
        return executiveReportPdfService.generate(inquiry);
    }

    @Transactional
    public InquiryDto updateStatus(UUID id, InquiryStatus status) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + id));
        InquiryStatus previous = inquiry.getStatus();
        inquiry.setStatus(status);
        InquiryDto saved = toDto(inquiryRepository.save(inquiry));
        auditLogService.recordBestEffort(AuditAction.LEAD_STATUS_CHANGE, "Inquiry", id.toString(),
                Map.of("from", String.valueOf(previous), "to", String.valueOf(status)));
        return saved;
    }

    Inquiry getEntity(UUID id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + id));
    }

    private String buildSummaryMessage(EstimatorRequest request, String integrationsText) {
        // The existing admin UI and confirmation email both render `message` as free text —
        // synthesizing one here means neither needs to change to understand an estimator lead.
        StringBuilder sb = new StringBuilder();
        sb.append(request.intent()).append(" — ").append(nullToDash(request.projectType())).append("\n\n");
        if (request.scopeDetails() != null && !request.scopeDetails().isBlank()) {
            sb.append("Scope: ").append(request.scopeDetails()).append("\n");
        }
        if (request.existingSystem() != null && !request.existingSystem().isBlank()) {
            sb.append("Existing system: ").append(request.existingSystem()).append("\n");
        }
        if (request.usersScale() != null && !request.usersScale().isBlank()) {
            sb.append("Users/scale: ").append(request.usersScale()).append("\n");
        }
        if (integrationsText != null) {
            sb.append("Integrations: ").append(integrationsText).append("\n");
        }
        if (request.timeline() != null && !request.timeline().isBlank()) {
            sb.append("Timeline: ").append(request.timeline()).append("\n");
        }
        if (request.urgency() != null && !request.urgency().isBlank()) {
            sb.append("Urgency: ").append(request.urgency()).append("\n");
        }
        sb.append("Submitted via the project estimator.");
        return sb.toString();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private InquiryDto toDto(Inquiry i) {
        EstimateDto estimate = null;
        if (i.getIntent() != InquiryIntent.GENERAL) {
            estimate = EstimateDto.builder()
                    .low(i.getEstimateLow())
                    .high(i.getEstimateHigh())
                    .currency(i.getEstimateCurrency())
                    .disclaimer(GENERIC_ESTIMATE_DISCLAIMER)
                    .build();
        }

        return InquiryDto.builder()
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
                .estimate(estimate)
                .createdAt(i.getCreatedAt())
                .bookingUrl(resolveBookingUrl(i))
                .build();
    }

    /** Module 2: instant-booking trigger for Tier-1 (HOT) leads. Null whenever the
     *  feature is disabled, unconfigured, or the lead isn't Tier-1 -- see
     *  LeadScoringService#isTierOne and app.sales.* config. */
    private String resolveBookingUrl(Inquiry i) {
        if (!bookingEnabled || calendlyUrl == null || calendlyUrl.isBlank()) {
            return null;
        }
        return leadScoringService.isTierOne(i.getLeadTier()) ? calendlyUrl : null;
    }
}
