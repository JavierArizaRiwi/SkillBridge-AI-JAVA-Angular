package com.riwi.skillbridge.domain.service.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ThresholdRule Domain Service")
class ThresholdRuleTest {

    @Test
    @DisplayName("Should return false when max cosine similarity is below threshold")
    void shouldReturnFalseWhenBelowThreshold() {
        ThresholdRule rule = new ThresholdRule(0.70);
        assertFalse(rule.isSufficient(0.699999));
        assertFalse(rule.isSufficient(0.50));
        assertFalse(rule.isSufficient(0.0));
        assertFalse(rule.isSufficient(-0.5));
    }

    @Test
    @DisplayName("Should return true when max cosine similarity equals threshold exactly")
    void shouldReturnTrueWhenExactlyAtThreshold() {
        ThresholdRule rule = new ThresholdRule(0.70);
        assertTrue(rule.isSufficient(0.70));
    }

    @Test
    @DisplayName("Should return true when max cosine similarity is above threshold")
    void shouldReturnTrueWhenAboveThreshold() {
        ThresholdRule rule = new ThresholdRule(0.70);
        assertTrue(rule.isSufficient(0.700001));
        assertTrue(rule.isSufficient(0.85));
        assertTrue(rule.isSufficient(1.0));
    }

    @Test
    @DisplayName("Should evaluate only cosine similarity and never fused or rank scores (FR-006, ADR-0008)")
    void shouldEvaluateOnlyCosineSimilarityAndIgnoreFusedRankScores() {
        double minScoreThreshold = 0.75;
        ThresholdRule rule = new ThresholdRule(minScoreThreshold);

        // Scenario 1: A candidate chunk has a high RRF fused rank score (e.g. 0.033, top rank),
        // but its maximum cosine similarity is 0.65 (< 0.75). The rule must evaluate sufficiency
        // solely against the cosine similarity and reject the context.
        double chunkCosineSimilarity = 0.65;
        double fusedRrfScore = 0.033;
        assertFalse(rule.isSufficient(chunkCosineSimilarity),
                "Should reject context when max cosine similarity is below threshold regardless of RRF score: " + fusedRrfScore);

        // Scenario 2: A candidate chunk has lower rank or fused score but satisfies cosine threshold (0.76 >= 0.75).
        // The rule evaluates cosine similarity and accepts the context.
        double sufficientCosineSimilarity = 0.76;
        assertTrue(rule.isSufficient(sufficientCosineSimilarity),
                "Should accept context when max cosine similarity meets or exceeds threshold");
    }

    @Test
    @DisplayName("Should expose configured minimum score threshold")
    void shouldExposeThreshold() {
        ThresholdRule rule = new ThresholdRule(0.82);
        assertEquals(0.82, rule.minScoreThreshold());
        assertEquals(0.82, rule.getMinScoreThreshold());
    }

    @Test
    @DisplayName("Should reject NaN as threshold")
    void shouldRejectNaN() {
        assertThrows(IllegalArgumentException.class, () -> new ThresholdRule(Double.NaN));
    }
}
