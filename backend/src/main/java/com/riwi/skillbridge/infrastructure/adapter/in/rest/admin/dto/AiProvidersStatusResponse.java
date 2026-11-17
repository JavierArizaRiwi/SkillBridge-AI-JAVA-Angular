package com.riwi.skillbridge.infrastructure.adapter.in.rest.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * Administrative DTO reporting effective fallback chains and circuit breaker states across capabilities.
 * Strictly free of confidential information, credentials, or API keys.
 */
public record AiProvidersStatusResponse(
        String timestamp,
        Map<String, CapabilityStatus> capabilities
) {
    public record CapabilityStatus(
            String capability,
            List<CandidateStatus> chain
    ) {}

    public record CandidateStatus(
            String provider,
            String model,
            boolean enabled,
            int timeoutSeconds,
            String circuitState,
            float failureRate,
            int bufferedCalls,
            int failedCalls
    ) {}
}
