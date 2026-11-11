package com.riwi.skillbridge.application.port.out.event;

import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.util.UUID;

/**
 * Payload for the RecommendationGenerated event.
 * Published after the AI model successfully returns a recommendation.
 * Includes latency and the AI model used for analytics purposes.
 */
public record RecommendationGeneratedPayload(
        UUID recommendationId,
        UUID userId,
        String goal,
        RecommendationInputType inputType,
        String modelUsed,
        long latencyMs
) {}
