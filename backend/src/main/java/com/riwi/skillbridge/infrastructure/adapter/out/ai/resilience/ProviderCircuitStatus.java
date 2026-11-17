package com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience;

import java.util.Objects;

/**
 * Immutable snapshot of a candidate model's circuit breaker operational state.
 * Contains zero credentials or confidential details.
 */
public record ProviderCircuitStatus(
        String capability,
        String provider,
        String model,
        String circuitState,
        float failureRate,
        int bufferedCalls,
        int failedCalls,
        long notPermittedCalls
) {
    public ProviderCircuitStatus {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(circuitState, "circuitState must not be null");
    }

    public static ProviderCircuitStatus of(
            String capability,
            String provider,
            String model,
            String circuitState,
            float failureRate,
            int bufferedCalls,
            int failedCalls,
            long notPermittedCalls
    ) {
        return new ProviderCircuitStatus(
                capability,
                provider,
                model,
                circuitState,
                failureRate,
                bufferedCalls,
                failedCalls,
                notPermittedCalls
        );
    }
}
