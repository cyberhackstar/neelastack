package com.neelastack.service;

import com.neelastack.entity.LeadTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeadScoringServiceTest {

    private final LeadScoringService service = new LeadScoringService();

    @Test
    void everySignalPresent_scoresHotAndCapsAt100() {
        int score = service.score(
                "₹5,00,000+", "ASAP", "production down", "Legacy Java monolith",
                List.of("Razorpay", "Google OAuth", "Cloudinary"), "Business platform");

        assertThat(score).isEqualTo(100); // 30+20+15+15+10+10 = 100, capped
        assertThat(service.tier(score)).isEqualTo(LeadTier.HOT);
    }

    @Test
    void noSignals_scoresZeroAndNurture() {
        int score = service.score(null, null, null, null, null, null);

        assertThat(score).isZero();
        assertThat(service.tier(score)).isEqualTo(LeadTier.NURTURE);
    }

    @Test
    void highBudgetAndClearTimelineOnly_scoresWarmNotHot() {
        // 30 (budget) + 10 (clear timeline) = 40 -> below WARM_THRESHOLD (60), still NURTURE.
        // This documents the actual cutoff rather than assuming two signals is "warm enough."
        int score = service.score("₹5,00,000+", "3 months", null, null, null, null);

        assertThat(score).isEqualTo(40);
        assertThat(service.tier(score)).isEqualTo(LeadTier.NURTURE);
    }

    @Test
    void budgetPlusUrgencyPlusExistingSystem_crossesWarmThreshold() {
        // 30 + 20 + 15 = 65 -> WARM (60-79).
        int score = service.score("₹5,00,000+", "ASAP", null, "Existing Rails app", null, null);

        assertThat(score).isEqualTo(65);
        assertThat(service.tier(score)).isEqualTo(LeadTier.WARM);
    }

    @Test
    void notSureTimeline_doesNotCountAsClearTimeline() {
        int score = service.score(null, "Not sure yet", null, null, null, null);

        assertThat(score).isZero();
    }

    @Test
    void singleIntegration_doesNotCountAsComplex() {
        // Complexity bonus requires 2+ integrations, not just "has an integration."
        int scoreWithOne = service.score(null, null, null, null, List.of("Razorpay"), null);
        int scoreWithTwo = service.score(null, null, null, null, List.of("Razorpay", "Cloudinary"), null);

        assertThat(scoreWithOne).isZero();
        assertThat(scoreWithTwo).isEqualTo(15);
    }
}
