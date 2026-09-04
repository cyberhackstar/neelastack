package com.neelastack.service;

import com.neelastack.dto.inquiry.CaseStudyProofDto;
import com.neelastack.dto.inquiry.PublicQuotationDto;
import com.neelastack.dto.inquiry.QuotationDto;
import com.neelastack.dto.inquiry.QuotationLineItemDto;
import com.neelastack.dto.inquiry.QuotationRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.entity.PricingRule;
import com.neelastack.entity.Project;
import com.neelastack.entity.Quotation;
import com.neelastack.entity.QuotationLineItem;
import com.neelastack.entity.QuotationStatus;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.PricingRuleRepository;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.QuotationRepository;
import com.neelastack.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final InquiryRepository inquiryRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    /**
     * Best-effort keyword map from an inquiry's free-text project type to a
     * service-category key, used only when an admin creates a quotation without
     * explicitly setting one. Deliberately narrow and conservative — a miss
     * (null) is fine and just means no case study gets injected; a wrong guess
     * would show an irrelevant proof point, which is worse than none.
     */
    private static final Map<String, String> CATEGORY_KEYWORDS = Map.ofEntries(
            Map.entry("migration", "full-stack-migration"),
            Map.entry("moderniz", "full-stack-migration"),
            Map.entry("legacy", "full-stack-migration"),
            Map.entry("e-commerce", "ecommerce"),
            Map.entry("ecommerce", "ecommerce"),
            Map.entry("marketplace", "ecommerce"),
            Map.entry("saas", "saas-platform"),
            Map.entry("platform", "saas-platform"),
            Map.entry("crm", "crm"),
            Map.entry("payment", "payments"),
            Map.entry("mobile", "mobile"),
            Map.entry("api", "api-integration"),
            Map.entry("integration", "api-integration")
    );

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    @Transactional
    public PublicQuotationDto getByPublicToken(String token) {
        Quotation quotation = quotationRepository.findByPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));

        if (quotation.getStatus() == QuotationStatus.DRAFT) {
            // A draft was never sent — treat it as not found rather than leaking its existence.
            throw new ResourceNotFoundException("Quotation not found");
        }

        expireIfPastValidity(quotation);
        recordView(quotation);

        return toPublicDto(quotation);
    }

    private void recordView(Quotation quotation) {
        // Only counts real client opens (SENT/ACCEPTED/REJECTED — never a DRAFT, which is
        // rejected above before this is reached).
        LocalDateTime now = LocalDateTime.now();
        quotation.setViewCount(quotation.getViewCount() + 1);
        if (quotation.getFirstViewedAt() == null) {
            quotation.setFirstViewedAt(now);
        }
        quotation.setLastViewedAt(now);
        quotationRepository.save(quotation);
    }

    private PublicQuotationDto toPublicDto(Quotation quotation) {
        List<QuotationLineItemDto> items = quotation.getLineItems().stream()
                .map(li -> new QuotationLineItemDto(li.getDescription(), li.getAmount()))
                .toList();

        return PublicQuotationDto.builder()
                .title(quotation.getTitle())
                .scopeSummary(quotation.getScopeSummary())
                .lineItems(items)
                .totalAmount(quotation.getTotalAmount())
                .currency(quotation.getCurrency())
                .status(quotation.getStatus())
                .validUntil(quotation.getValidUntil())
                .clientName(quotation.getInquiry().getName())
                .relatedCaseStudy(resolveCaseStudy(quotation.getServiceCategory()))
                .build();
    }

    /**
     * Picks the strongest published, category-matching case study to show
     * alongside this proposal. Returns null (no block rendered) rather than any
     * kind of fallback when the quotation has no category or nothing matches —
     * see CaseStudyProofDto's javadoc for why an unrelated proof point is worse
     * than none.
     */
    private CaseStudyProofDto resolveCaseStudy(String serviceCategory) {
        if (serviceCategory == null || serviceCategory.isBlank()) {
            return null;
        }
        List<Project> matches = projectRepository.findBestMatchesForCategory(serviceCategory);
        if (matches.isEmpty()) {
            return null;
        }
        Project project = matches.get(0);
        Double averageRating = reviewRepository.findAverageRatingByProjectId(project.getId()).orElse(null);
        long reviewCount = reviewRepository.countByProjectIdAndPublishedTrue(project.getId());

        return CaseStudyProofDto.builder()
                .title(project.getTitle())
                .slug(project.getSlug())
                .summary(project.getSummary())
                .coverImageUrl(project.getCoverImageUrl())
                .keyMetrics(project.getKeyMetrics())
                .averageRating(averageRating != null ? Math.round(averageRating * 10) / 10.0 : null)
                .reviewCount((int) reviewCount)
                .build();
    }

    /** Best-effort inference used only when an admin doesn't set serviceCategory
     *  explicitly on QuotationRequest — see CATEGORY_KEYWORDS. */
    private String inferServiceCategory(String projectType) {
        if (projectType == null || projectType.isBlank()) {
            return null;
        }
        String haystack = projectType.toLowerCase(Locale.ROOT);
        return CATEGORY_KEYWORDS.entrySet().stream()
                .filter(e -> haystack.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public PublicQuotationDto respondToQuotation(String token, boolean accept, String reason) {
        Quotation quotation = quotationRepository.findByPublicToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));

        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new BadRequestException("This quotation has already been responded to, or was never sent");
        }

        expireIfPastValidity(quotation);
        if (quotation.getStatus() == QuotationStatus.EXPIRED) {
            throw new BadRequestException("This quotation has expired and can no longer be accepted or rejected");
        }

        LocalDateTime now = LocalDateTime.now();
        quotation.setStatus(accept ? QuotationStatus.ACCEPTED : QuotationStatus.REJECTED);
        quotation.setResponseReason(reason);
        quotation.setRespondedAt(now);
        if (accept) {
            quotation.setAcceptedAt(now);
        } else {
            quotation.setRejectedAt(now);
        }
        Quotation saved = quotationRepository.save(quotation);

        emailService.sendQuotationResponseNotice(saved, accept, reason);

        auditLogService.recordBestEffort(AuditAction.QUOTATION_RESPONDED, "Quotation", saved.getId().toString(),
                Map.of("accepted", accept, "reason", reason == null ? "" : reason));

        return toPublicDto(saved);
    }

    @Transactional
    public QuotationDto create(QuotationRequest request) {
        Inquiry inquiry = inquiryRepository.findById(request.inquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + request.inquiryId()));

        List<QuotationLineItem> lineItems = request.lineItems().stream()
                .map(li -> QuotationLineItem.builder().description(li.description()).amount(li.amount()).build())
                .toList();

        BigDecimal total = lineItems.stream()
                .map(QuotationLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Pricing version traceability (V21): if the admin referenced a PricingRule while
        // building this quotation, snapshot its current version now -- the rule row can be
        // revised later, and this quotation should keep recording what was actually true
        // at creation time, not whatever the rule says today. An unknown/missing id is
        // treated the same as "no rule referenced" rather than failing the whole request;
        // the quotation's line items and total are the source of truth either way.
        UUID pricingRuleId = null;
        Integer pricingRuleVersion = null;
        if (request.pricingRuleId() != null) {
            PricingRule rule = pricingRuleRepository.findById(request.pricingRuleId()).orElse(null);
            if (rule != null) {
                pricingRuleId = rule.getId();
                pricingRuleVersion = rule.getVersion();
            } else {
                log.warn("QuotationRequest referenced unknown pricingRuleId {} — leaving traceability fields null",
                        request.pricingRuleId());
            }
        }

        String serviceCategory = request.serviceCategory() != null && !request.serviceCategory().isBlank()
                ? request.serviceCategory()
                : inferServiceCategory(inquiry.getProjectType());

        Quotation quotation = Quotation.builder()
                .inquiry(inquiry)
                .title(request.title())
                .scopeSummary(request.scopeSummary())
                .lineItems(lineItems)
                .totalAmount(total)
                .currency(request.currency() != null ? request.currency() : "INR")
                .status(QuotationStatus.DRAFT)
                .validUntil(request.validUntil())
                .notes(request.notes())
                .pricingRuleId(pricingRuleId)
                .pricingRuleVersion(pricingRuleVersion)
                .serviceCategory(serviceCategory)
                .build();

        Quotation saved = quotationRepository.save(quotation);
        QuotationDto dto = toDto(saved);
        auditLogService.recordBestEffort(AuditAction.QUOTATION_CREATED, "Quotation", saved.getId().toString(),
                Map.of("inquiryId", inquiry.getId().toString(), "totalAmount", String.valueOf(total)));
        return dto;
    }

    public List<QuotationDto> listForInquiry(UUID inquiryId) {
        return quotationRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public QuotationDto send(UUID quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found: " + quotationId));

        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new BadRequestException("Only a DRAFT quotation can be sent");
        }

        quotation.setStatus(QuotationStatus.SENT);
        quotation.setSentAt(LocalDateTime.now());
        Quotation saved = quotationRepository.save(quotation);

        Inquiry inquiry = saved.getInquiry();
        inquiry.setStatus(InquiryStatus.QUOTED);
        inquiryRepository.save(inquiry);

        emailService.sendQuotation(saved);

        auditLogService.recordBestEffort(AuditAction.QUOTATION_DISPATCHED, "Quotation", saved.getId().toString(), null);

        return toDto(saved);
    }

    /**
     * validUntil was tracked but never enforced — a SENT quotation stayed acceptable
     * forever unless someone manually flipped its status. This closes that gap at the
     * two points that matter most (viewing and responding), on top of the nightly sweep
     * below, so an expired quotation can't be accepted even in the narrow window before
     * the sweep next runs.
     */
    private void expireIfPastValidity(Quotation quotation) {
        if (quotation.getStatus() == QuotationStatus.SENT
                && quotation.getValidUntil() != null
                && quotation.getValidUntil().isBefore(LocalDateTime.now().toLocalDate())) {
            quotation.setStatus(QuotationStatus.EXPIRED);
            quotationRepository.save(quotation);
        }
    }

    @Scheduled(cron = "0 0 1 * * *") // 1 AM daily
    @Transactional
    public void expireOverdueQuotations() {
        List<Quotation> overdue = quotationRepository.findByStatusAndValidUntilBefore(
                QuotationStatus.SENT, LocalDateTime.now().toLocalDate());
        overdue.forEach(q -> q.setStatus(QuotationStatus.EXPIRED));
        if (!overdue.isEmpty()) {
            quotationRepository.saveAll(overdue);
            log.info("Marked {} quotation(s) as EXPIRED", overdue.size());
        }
    }

    private QuotationDto toDto(Quotation q) {
        List<QuotationLineItemDto> items = q.getLineItems().stream()
                .map(li -> new QuotationLineItemDto(li.getDescription(), li.getAmount()))
                .toList();

        return QuotationDto.builder()
                .id(q.getId())
                .inquiryId(q.getInquiry().getId())
                .title(q.getTitle())
                .scopeSummary(q.getScopeSummary())
                .lineItems(items)
                .totalAmount(q.getTotalAmount())
                .currency(q.getCurrency())
                .status(q.getStatus())
                .validUntil(q.getValidUntil())
                .notes(q.getNotes())
                .responseReason(q.getResponseReason())
                .respondedAt(q.getRespondedAt())
                .sentAt(q.getSentAt())
                .viewCount(q.getViewCount())
                .lastViewedAt(q.getLastViewedAt())
                .firstViewedAt(q.getFirstViewedAt())
                .acceptedAt(q.getAcceptedAt())
                .rejectedAt(q.getRejectedAt())
                .responseTimeHours(responseTimeHours(q))
                .createdAt(q.getCreatedAt())
                .pricingRuleId(q.getPricingRuleId())
                .pricingRuleVersion(q.getPricingRuleVersion())
                .serviceCategory(q.getServiceCategory())
                .build();
    }

    /** Hours from sentAt to whichever of acceptedAt/rejectedAt is set — null while still open. */
    private Long responseTimeHours(Quotation q) {
        LocalDateTime respondedAt = q.getAcceptedAt() != null ? q.getAcceptedAt() : q.getRejectedAt();
        if (q.getSentAt() == null || respondedAt == null) {
            return null;
        }
        return java.time.Duration.between(q.getSentAt(), respondedAt).toHours();
    }
}
