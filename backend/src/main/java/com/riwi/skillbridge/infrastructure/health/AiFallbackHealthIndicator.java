package com.riwi.skillbridge.infrastructure.health;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.ProviderCircuitStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated Spring Boot Actuator HealthIndicator for AI fallback and resilience status.
 *
 * Reports:
 * - UP: All candidate circuit breakers are healthy (CLOSED).
 * - DEGRADED: One or more candidate circuits are OPEN/HALF_OPEN, but fallback candidates remain available.
 * - DOWN: All candidate circuits in an entire capability are OPEN (exhaustion/total failure).
 *
 * Exposes zero internal API keys or secrets.
 */
@Component
public class AiFallbackHealthIndicator implements HealthIndicator {

    private final AiCircuitBreakerManager circuitBreakerManager;

    public AiFallbackHealthIndicator(AiCircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
    }

    @Override
    public Health health() {
        List<ProviderCircuitStatus> statuses = circuitBreakerManager.getAllStatuses();
        if (statuses.isEmpty()) {
            return Health.up().withDetail("status", "No AI circuits registered").build();
        }

        int total = statuses.size();
        long openCount = statuses.stream().filter(s -> "OPEN".equalsIgnoreCase(s.circuitState())).count();
        long halfOpenCount = statuses.stream().filter(s -> "HALF_OPEN".equalsIgnoreCase(s.circuitState())).count();

        // Check per capability if all candidates are OPEN
        Map<String, List<ProviderCircuitStatus>> byCapability = statuses.stream()
                .collect(Collectors.groupingBy(ProviderCircuitStatus::capability));

        boolean anyCapabilityCompletelyDown = byCapability.values().stream()
                .anyMatch(capStatuses -> !capStatuses.isEmpty() && capStatuses.stream().allMatch(s -> "OPEN".equalsIgnoreCase(s.circuitState())));

        if (anyCapabilityCompletelyDown) {
            return Health.down()
                    .withDetail("status", "All candidates down in at least one AI capability")
                    .withDetail("totalCircuits", total)
                    .withDetail("openCircuits", openCount)
                    .withDetail("halfOpenCircuits", halfOpenCount)
                    .build();
        }

        if (openCount > 0 || halfOpenCount > 0) {
            return Health.status("DEGRADED")
                    .withDetail("status", "One or more AI candidate circuits open/half-open, fallback operational")
                    .withDetail("totalCircuits", total)
                    .withDetail("openCircuits", openCount)
                    .withDetail("halfOpenCircuits", halfOpenCount)
                    .build();
        }

        return Health.up()
                .withDetail("status", "All AI candidate circuits operational (CLOSED)")
                .withDetail("totalCircuits", total)
                .withDetail("openCircuits", 0)
                .withDetail("halfOpenCircuits", 0)
                .build();
    }
}
