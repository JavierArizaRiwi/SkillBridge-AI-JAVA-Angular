package com.riwi.skillbridge.application.port.out.event;

import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.util.List;
import java.util.UUID;

/**
 * Payload for the RecommendationGenerated event (v2).
 * Published after the AI model successfully returns a recommendation.
 * offeringIds lists the offerings actually recommended, so analytics can attribute demand per offering.
 * Includes latency and the AI provider and model used for analytics and traceability.
 */
public record RecommendationGeneratedPayload(
        UUID recommendationId,
        UUID userId,
        String goal,
        RecommendationInputType inputType,
        String modelUsed,
        long latencyMs,
        String provider,
        List<UUID> offeringIds
) {
    public RecommendationGeneratedPayload {
        offeringIds = offeringIds == null ? List.of() : List.copyOf(offeringIds);
    }

    /** Backward-compatible 7-argument constructor (events published before offeringIds existed). */
    public RecommendationGeneratedPayload(UUID recommendationId, UUID userId, String goal,
            RecommendationInputType inputType, String modelUsed, long latencyMs, String provider) {
        this(recommendationId, userId, goal, inputType, modelUsed, latencyMs, provider, List.of());
    }

    /**
     * Backward-compatible 6-argument constructor for existing consumers.
     */
    public RecommendationGeneratedPayload(
            UUID recommendationId,
            UUID userId,
            String goal,
            RecommendationInputType inputType,
            String modelUsed,
            long latencyMs
    ) {
        this(recommendationId, userId, goal, inputType, modelUsed, latencyMs, null);
    }
}
