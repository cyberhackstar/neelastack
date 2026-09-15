package com.neelastack.service;

import com.neelastack.dto.inquiry.BusinessAuditFindingDto;
import com.neelastack.dto.inquiry.BusinessAuditPreviewRequest;
import com.neelastack.dto.inquiry.BusinessAuditPreviewResultDto;
import com.neelastack.dto.inquiry.BusinessAuditUnlockRequest;
import com.neelastack.dto.inquiry.BusinessAuditUnlockResultDto;
import com.neelastack.dto.inquiry.InquiryDto;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryIntent;
import com.neelastack.entity.InquiryStatus;
import com.neelastack.repository.InquiryRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BusinessAuditService {
    private final InquiryRepository inquiryRepository;
    private final EmailService emailService;
    private final LeadScoringService leadScoringService;
    private final ExecutiveReportDispatchService executiveReportDispatchService;
    private final InquiryService inquiryService;

    private static final List<Finding> FINDINGS = List.of(
            finding("Website conversion", 28, "HIGH", "Your website is not yet doing enough to move visitors to a clear next action.", "Give every important visitor one obvious next step: enquire, book, buy or call.", "No website"),
            finding("Customer journey", 24, "HIGH", "Customers still have to work out what to do next instead of moving through a designed journey.", "Create a simple path from discovery to proof to action, especially on mobile.", "Information only"),
            finding("Lead capture", 20, "MEDIUM", "Interested visitors can fall away when enquiries depend on manual messages or scattered channels.", "Add structured lead capture with source attribution and a clear follow-up workflow.", "Manual / unclear"),
            finding("Local discovery", 16, "MEDIUM", "Your digital presence may be missing important local discovery and trust signals.", "Strengthen location, service and business information so searchers can find and trust you.", "Weak / unsure"),
            finding("Online action", 22, "HIGH", "Customers cannot complete a valuable action online as easily as they should.", "Move booking, ordering, purchasing or consultation requests into a friction-light digital flow.", "Mostly offline"),
            finding("Growth foundation", 14, "MEDIUM", "Your digital presence may be acting as a brochure instead of a measurable business asset.", "Connect pages, campaigns and lead capture to measurable conversion events and a repeatable sales process.", "Basic presence")
    );

    public BusinessAuditPreviewResultDto preview(BusinessAuditPreviewRequest request) {
        Report report = calculate(request.industry(), request.websitePresence(), request.customerAction(), request.leadCapture(), request.localDiscovery(), request.primaryGoal());
        List<Finding> matched = report.findings();
        List<String> teaser = matched.stream().sorted(Comparator.comparingInt(Finding::weight).reversed()).limit(2)
                .map(f -> f.title() + " — " + f.priority().toLowerCase(Locale.ROOT) + " opportunity")
                .toList();
        return BusinessAuditPreviewResultDto.builder()
                .score(report.score()).level(level(report.score())).teaserFindings(teaser)
                .lockedFindingsCount(Math.max(0, matched.size() - teaser.size()))
                .disclaimer("This is a guided self-assessment based on the answers you selected, not an automated scan of your website, analytics or business data.")
                .build();
    }

    @Transactional
    public BusinessAuditUnlockResultDto unlock(BusinessAuditUnlockRequest request) {
        Report report = calculate(request.industry(), request.websitePresence(), request.customerAction(), request.leadCapture(), request.localDiscovery(), request.primaryGoal());
        int leadScore = Math.min(100, leadScoringService.score(null, null, null, request.websitePresence(), List.of(request.customerAction(), request.leadCapture()), "Business digital audit")
                + (report.score() >= 70 ? 20 : report.score() >= 45 ? 10 : 0));

        String message = buildMessage(request, report);
        Inquiry inquiry = Inquiry.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .company(request.company())
                .projectType("Business digital audit")
                .message(message)
                .status(InquiryStatus.NEW)
                .source("business_audit")
                .intent(InquiryIntent.BUILD)
                .existingSystem(request.website())
                .scopeDetails("Industry: " + request.industry() + "; City: " + nullToDash(request.city())
                        + "; Primary goal: " + request.primaryGoal())
                .integrations(request.customerAction() + "; " + request.leadCapture())
                .leadScore(leadScore)
                .leadTier(leadScoringService.tier(leadScore))
                .utmSource(request.utmSource()).utmMedium(request.utmMedium()).utmCampaign(request.utmCampaign())
                .referrer(request.referrer()).landingPage(request.landingPage())
                .build();

        Inquiry saved = inquiryRepository.saveAndFlush(inquiry);
        emailService.sendInquiryConfirmation(saved);
        emailService.sendAdminNewInquiryAlert(saved);
        executiveReportDispatchService.dispatch(saved);

        List<BusinessAuditFindingDto> findings = report.findings().stream()
                .sorted(Comparator.comparingInt(Finding::weight).reversed())
                .map(f -> BusinessAuditFindingDto.builder().title(f.title()).priority(f.priority())
                        .summary(f.summary()).opportunity(f.opportunity()).build())
                .toList();
        List<String> recommendations = recommendations(request.industry(), request.primaryGoal());

        return BusinessAuditUnlockResultDto.builder()
                .inquiry(inquiryService.get(saved.getId()))
                .score(report.score()).level(level(report.score())).findings(findings)
                .recommendations(recommendations)
                .disclaimer("Your score is a decision aid, not a technical audit. A strategy call is the right next step for a real business-specific recommendation.")
                .build();
    }

    private Report calculate(String industry, String websitePresence, String customerAction, String leadCapture, String localDiscovery, String primaryGoal) {
        List<Finding> matched = new ArrayList<>();
        addByAnswer(matched, websitePresence);
        addByAnswer(matched, customerAction);
        addByAnswer(matched, leadCapture);
        addByAnswer(matched, localDiscovery);
        int goalBonus = primaryGoal != null && !primaryGoal.isBlank() ? 5 : 0;
        int industryBonus = industry != null && !industry.isBlank() ? 3 : 0;
        int score = Math.min(100, matched.stream().mapToInt(Finding::weight).sum() + goalBonus + industryBonus);
        return new Report(score, matched);
    }

    private void addByAnswer(List<Finding> matched, String answer) {
        String normalized = answer == null ? "" : answer.trim();
        FINDINGS.stream().filter(f -> f.answer().equalsIgnoreCase(normalized)).findFirst().ifPresent(matched::add);
        if ("Not sure".equalsIgnoreCase(normalized)) {
            FINDINGS.stream().filter(f -> "Not sure".equalsIgnoreCase(f.answer())).findFirst().ifPresent(matched::add);
        }
    }

    private List<String> recommendations(String industry, String goal) {
        String lower = industry == null ? "" : industry.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (lower.contains("gym") || lower.contains("fitness")) out.add("Prioritise membership discovery, class booking and enquiry conversion.");
        else if (lower.contains("restaurant")) out.add("Prioritise menu discovery, reservations, ordering and local search intent.");
        else if (lower.contains("clinic") || lower.contains("health")) out.add("Prioritise trust, service discovery, appointment booking and enquiry capture.");
        else if (lower.contains("retail") || lower.contains("e-commerce") || lower.contains("clothing")) out.add("Prioritise product discovery, mobile checkout and repeat-customer journeys.");
        else out.add("Prioritise a clear positioning page, strong lead capture and a measurable conversion path.");
        out.add("Instrument every important conversion so future campaigns can be evaluated by qualified leads, not clicks alone.");
        if (goal != null && !goal.isBlank()) out.add("Build the first release around your primary goal: " + goal.toLowerCase(Locale.ROOT) + ".");
        return out;
    }

    private String buildMessage(BusinessAuditUnlockRequest request, Report report) {
        return "Business digital audit score: " + report.score() + " (" + level(report.score()) + ")\n\n"
                + "Industry: " + request.industry() + "\n"
                + "Website: " + nullToDash(request.website()) + "\n"
                + "City: " + nullToDash(request.city()) + "\n"
                + "Website presence: " + request.websitePresence() + "\n"
                + "Customer action: " + request.customerAction() + "\n"
                + "Lead capture: " + request.leadCapture() + "\n"
                + "Local discovery: " + request.localDiscovery() + "\n"
                + "Primary goal: " + request.primaryGoal() + "\n\n"
                + "Submitted via the Free Business Audit lead magnet.";
    }

    private static String level(int score) {
        if (score >= 70) return "NEEDS ATTENTION";
        if (score >= 45) return "ROOM TO GROW";
        return "SOLID FOUNDATION";
    }

    private static String nullToDash(String value) { return value == null || value.isBlank() ? "—" : value; }

    private static Finding finding(String title, int weight, String priority, String summary, String opportunity, String answer) {
        return new Finding(title, weight, priority, summary, opportunity, answer);
    }

    private record Finding(String title, int weight, String priority, String summary, String opportunity, String answer) {}
    private record Report(int score, List<Finding> findings) {}
}
