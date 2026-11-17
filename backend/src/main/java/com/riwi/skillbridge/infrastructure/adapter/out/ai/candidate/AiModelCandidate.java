package com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate;

import java.util.Objects;

/**
 * Immutable representation of a candidate AI model configured in the fallback chain.
 *
 * @param providerName     Identifier of the provider (e.g., "gemini", "openai", "groq", "elevenlabs")
 * @param modelIdentifier  Identifier of the specific model (e.g., "gemini-3.8-flash", "gpt-4o-mini")
 * @param priorityOrder    Priority in the execution chain (lower values evaluated first)
 * @param timeoutSeconds   Execution timeout dedicated to this candidate
 * @param isConfigured     Whether the candidate has required credentials/configuration to execute
 */
public record AiModelCandidate(
        String providerName,
        String modelIdentifier,
        int priorityOrder,
        int timeoutSeconds,
        boolean isConfigured
) {
    public AiModelCandidate {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(modelIdentifier, "modelIdentifier must not be null");
        if (priorityOrder < 0) {
            throw new IllegalArgumentException("priorityOrder must be non-negative");
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10; // Default fallback timeout if non-positive specified
        }
    }

    public static AiModelCandidate of(String providerName, String modelIdentifier, int priorityOrder, int timeoutSeconds) {
        return new AiModelCandidate(providerName, modelIdentifier, priorityOrder, timeoutSeconds, true);
    }

    public String candidateKey() {
        return providerName + ":" + modelIdentifier;
    }
}
