package com.riwi.skillbridge.application.port.out.event;

import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.util.UUID;

/**
 * Payload for the RecommendationRequested event.
 * Published when a user submits a recommendation request before calling the AI.
 */
public record RecommendationRequestedPayload(
        UUID recommendationId,
        UUID userId,
        String goal,
        RecommendationInputType inputType
) {}
