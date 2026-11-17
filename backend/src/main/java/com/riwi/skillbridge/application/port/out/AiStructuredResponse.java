package com.riwi.skillbridge.application.port.out;

import java.util.List;

/**
 * Structured response returned by AI recommendation ports.
 * Includes candidate resolution metadata (provider and model) for auditability and events.
 */
public record AiStructuredResponse(
        String explanation,
        List<AiItem> recommendations,
        String resolvedByProvider,
        String resolvedByModel
) {
    public record AiItem(String offeringId, double score, String reason) {}

    public AiStructuredResponse(String explanation, List<AiItem> recommendations) {
        this(explanation, recommendations, null, null);
    }
}
