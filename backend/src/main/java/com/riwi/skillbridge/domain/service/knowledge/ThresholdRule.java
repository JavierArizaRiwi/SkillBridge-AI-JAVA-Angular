package com.riwi.skillbridge.domain.service.knowledge;

/**
 * Domain rule evaluating whether the highest cosine similarity score retrieved
 * satisfies the minimum relevance threshold (FR-006, ADR-0008).
 *
 * <p>The rule strictly evaluates the raw cosine similarity score rather than any
 * fused rank score (such as RRF), ensuring that knowledge context is only deemed
 * sufficient when semantic similarity meets or exceeds the configured threshold.
 *
 * <p>Restricted to pure Java 21 without external dependencies, Spring, or JPA.
 */
public final class ThresholdRule {

    private final double minScoreThreshold;

    public ThresholdRule(double minScoreThreshold) {
        if (Double.isNaN(minScoreThreshold)) {
            throw new IllegalArgumentException("minScoreThreshold cannot be NaN");
        }
        this.minScoreThreshold = minScoreThreshold;
    }

    /**
     * Determines whether the maximum cosine similarity score is sufficient to proceed
     * with generative response generation.
     *
     * @param maxCosineSimilarity the maximum cosine similarity score among retrieved chunks
     * @return true if maxCosineSimilarity >= minScoreThreshold, false otherwise
     */
    public boolean isSufficient(double maxCosineSimilarity) {
        return maxCosineSimilarity >= minScoreThreshold;
    }

    public double minScoreThreshold() {
        return minScoreThreshold;
    }

    public double getMinScoreThreshold() {
        return minScoreThreshold;
    }
}
