package com.neelastack.service;

import com.neelastack.dto.inquiry.EstimateDto;
import com.neelastack.dto.pricing.PricingRuleDto;
import com.neelastack.entity.InquiryIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Produces a rough, illustrative estimate range from the estimator intake — never a
 * quotation (master prompt section 21 requires that distinction to be explicit).
 *
 * P0 pricing fix: every number here comes from an active {@link PricingRuleDto}
 * (admin-managed via {@code AdminPricingController}, backed by the {@code pricing_rules}
 * table) — this class never hardcodes a base range or a multiplier. If nobody has
 * configured an active rule for a category, the honest answer is "needs a scoped
 * conversation," not an invented number.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EstimateCalculatorService {

    private static final String DISCLAIMER =
            "This is a preliminary, automatically generated estimate based on the information " +
            "provided — not a binding quotation. A detailed, fixed-scope proposal follows a " +
            "short discovery conversation.";

    private static final String NO_RULE_DISCLAIMER =
            "This looks like a larger or custom engagement, so it needs a scoped conversation " +
            "rather than an automatic range. " + DISCLAIMER;

    private final PricingRuleService pricingRuleService;

    public EstimateDto calculate(InquiryIntent intent, String projectType, String existingSystem,
                                  List<String> integrations, String timeline) {
        return calculate(intent, projectType, existingSystem, integrations, timeline, null, null);
    }

    public EstimateDto calculate(InquiryIntent intent, String projectType, String existingSystem,
                                  List<String> integrations, String timeline,
                                  String usersScale, String urgency) {
        String serviceKey = resolveServiceKey(intent, projectType);
        Optional<PricingRuleDto> ruleOpt = pricingRuleService.getActiveRule(serviceKey);

        if (ruleOpt.isEmpty()) {
            log.warn("No active pricing rule configured for service key '{}' — returning a " +
                    "scoped-conversation response instead of guessing a number.", serviceKey);
            return EstimateDto.builder()
                    .low(null).high(null).currency("INR")
                    .disclaimer(NO_RULE_DISCLAIMER)
                    .build();
        }

        PricingRuleDto rule = ruleOpt.get();
        if (rule.baseHigh() == null) {
            // Enterprise/custom scope — no responsible automatic range to quote.
            return EstimateDto.builder()
                    .low(null).high(null).currency("INR")
                    .disclaimer(NO_RULE_DISCLAIMER)
                    .build();
        }

        BigDecimal low = rule.baseLow();
        BigDecimal high = rule.baseHigh();

        int integrationCount = integrations == null ? 0 : integrations.size();
        if (integrationCount > 2) {
            BigDecimal bump = high.multiply(rule.integrationFactor())
                    .multiply(BigDecimal.valueOf(integrationCount - 2));
            high = high.add(bump);
        }

        if (intent == InquiryIntent.MODERNIZE && existingSystem != null && !existingSystem.isBlank()) {
            // Legacy migrations routinely uncover scope that isn't visible up front.
            high = high.add(high.multiply(rule.complexityFactor()));
        }

        if (isLargeScale(usersScale)) {
            high = high.add(high.multiply(rule.scaleFactor()));
        }

        if (isUrgent(urgency, timeline)) {
            high = high.add(high.multiply(rule.urgencyFactor()));
            low = low.add(low.multiply(rule.urgencyFactor().divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP)));
        }

        return EstimateDto.builder()
                .low(low.setScale(0, RoundingMode.HALF_UP))
                .high(high.setScale(0, RoundingMode.HALF_UP))
                .currency("INR")
                .disclaimer(DISCLAIMER)
                .build();
    }

    /**
     * Maps the free-text estimator intake to one of the pricing_rules service keys
     * seeded in V18 (audit-review / api-backend / enterprise-platform / fix /
     * full-stack). This mapping logic is the only thing that stays in code — the
     * actual prices behind each key are entirely admin-configurable.
     */
    private String resolveServiceKey(InquiryIntent intent, String projectType) {
        String p = projectType == null ? "" : projectType.toLowerCase(Locale.ROOT);

        if (p.contains("audit") || p.contains("review")) {
            return "audit-review";
        }
        if (p.contains("api") || p.contains("backend")) {
            return "api-backend";
        }
        if (p.contains("enterprise") || p.contains("platform") || p.contains("marketplace")) {
            return "enterprise-platform";
        }
        if (intent == InquiryIntent.FIX) {
            return "fix";
        }
        return "full-stack";
    }

    /** Best-effort heuristic on the free-text "expected users/scale" field. */
    private boolean isLargeScale(String usersScale) {
        if (usersScale == null || usersScale.isBlank()) {
            return false;
        }
        String s = usersScale.toLowerCase(Locale.ROOT);
        return s.contains("enterprise") || s.contains("million") || s.contains("100k")
                || s.contains("100,000") || s.contains("10k+") || s.contains("50,000")
                || s.matches(".*\\b([1-9][0-9]{2,}),?[0-9]{3}\\+?\\b.*"); // e.g. "200,000 users"
    }

    /** Best-effort heuristic combining the explicit urgency field and the timeline text. */
    private boolean isUrgent(String urgency, String timeline) {
        String u = (urgency == null ? "" : urgency) + " " + (timeline == null ? "" : timeline);
        u = u.toLowerCase(Locale.ROOT);
        return u.contains("asap") || u.contains("urgent") || u.contains("rush")
                || u.contains("immediately") || u.contains("1 week") || u.contains("2 week");
    }
}
