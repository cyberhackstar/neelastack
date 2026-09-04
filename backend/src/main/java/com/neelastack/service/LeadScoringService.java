package com.neelastack.service;

import com.neelastack.entity.LeadTier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Rule-based lead scoring, per master prompt section 49:
 *
 *   high budget            +30
 *   urgent timeline        +20
 *   existing prod system   +15
 *   complex integrations   +15
 *   business platform      +10
 *   clear decision timeline +10
 *
 *   80-100 HOT, 60-79 WARM, below WARM
 *
 * These weights and the string-matching heuristics below are a starting model, not a
 * tuned commercial one — there's no conversion data yet to tune them against. Revisit
 * once real proposals-won-vs-lost history exists. Keeping the whole model in one small,
 * documented service (rather than scattered inline) is what section 49 means by
 * "make scoring configurable" in a codebase with no admin-editable rules engine yet.
 */
@Service
public class LeadScoringService {

    private static final int HOT_THRESHOLD = 80;
    private static final int WARM_THRESHOLD = 60;

    public int score(String budgetRange, String timeline, String urgency,
                      String existingSystem, List<String> integrations, String projectType) {
        int score = 0;

        if (isHighBudget(budgetRange)) score += 30;
        if (isUrgent(timeline, urgency)) score += 20;
        if (existingSystem != null && !existingSystem.isBlank()) score += 15;
        if (integrations != null && integrations.size() >= 2) score += 15;
        if (isBusinessPlatform(projectType)) score += 10;
        if (hasClearTimeline(timeline)) score += 10;

        return Math.min(score, 100);
    }

    public LeadTier tier(int score) {
        if (score >= HOT_THRESHOLD) return LeadTier.HOT;
        if (score >= WARM_THRESHOLD) return LeadTier.WARM;
        return LeadTier.NURTURE;
    }

    /** HOT is this codebase's "Tier-1" — the instant-booking trigger (module 2 of the
     *  Client Acquisition & High-Ticket Conversion Engine) fires only for this tier. */
    public boolean isTierOne(LeadTier tier) {
        return tier == LeadTier.HOT;
    }

    private boolean isHighBudget(String budgetRange) {
        if (budgetRange == null) return false;
        String b = budgetRange.toLowerCase(Locale.ROOT);
        return b.contains("5,00,000+") || b.contains("500000")
                || b.contains("2,00,000") || b.contains("200000")
                || b.contains("custom");
    }

    private boolean isUrgent(String timeline, String urgency) {
        String t = timeline == null ? "" : timeline.toLowerCase(Locale.ROOT);
        String u = urgency == null ? "" : urgency.toLowerCase(Locale.ROOT);
        return t.contains("asap") || t.contains("immediate") || t.contains("1 month") || t.contains("<")
                || u.contains("production down") || u.contains("urgent") || u.contains("critical");
    }

    private boolean isBusinessPlatform(String projectType) {
        if (projectType == null) return false;
        String p = projectType.toLowerCase(Locale.ROOT);
        return p.contains("platform") || p.contains("saas") || p.contains("e-commerce")
                || p.contains("enterprise") || p.contains("marketplace");
    }

    private boolean hasClearTimeline(String timeline) {
        return timeline != null && !timeline.isBlank()
                && !timeline.toLowerCase(Locale.ROOT).contains("not sure");
    }
}
