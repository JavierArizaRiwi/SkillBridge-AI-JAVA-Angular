package com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import com.riwi.skillbridge.infrastructure.config.AiFallbackProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerManagerTest {

    private AiFallbackProperties properties;
    private AiErrorClassifier errorClassifier;
    private AiCircuitBreakerManager manager;

    @BeforeEach
    void setUp() {
        properties = new AiFallbackProperties();
        // Configure low minimum calls and fast open duration for testing
        AiFallbackProperties.CircuitBreakerProperties cbProps = properties.getChat().getCircuitBreaker();
        cbProps.setMinimumNumberOfCalls(2);
        cbProps.setSlidingWindowSize(2);
        cbProps.setFailureRateThreshold(50.0f);
        cbProps.setWaitDurationInOpenStateSeconds(1);
        cbProps.setPermittedNumberOfCallsInHalfOpenState(1);

        errorClassifier = new AiErrorClassifier();
        manager = new AiCircuitBreakerManager(properties, errorClassifier);
    }

    @Test
    @DisplayName("Scenario 2.1: Circuit transitions to OPEN when recoverable failure threshold is exceeded")
    void shouldOpenCircuitOnRecoverableFailures() {
        String capability = "chat";
        String provider = "gemini";
        String model = "gemini-3.8-flash";

        CircuitBreaker cb = manager.getOrCreateCircuitBreaker(capability, provider, model);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Record 2 recoverable failures (TIMEOUT, 503 UNAVAILABLE)
        manager.onError(capability, provider, model, 10, new AiProviderException("Timeout", AiProviderException.ErrorType.TIMEOUT));
        manager.onError(capability, provider, model, 10, new AiProviderException("Unavailable", AiProviderException.ErrorType.UNAVAILABLE));

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Scenario 2.2: Omit provider with OPEN circuit in < 5 ms without waiting")
    void shouldSkipCandidateImmediatelyWhenCircuitIsOpen() {
        String capability = "chat";
        String provider = "gemini";
        String model = "gemini-3.8-flash";

        CircuitBreaker cb = manager.getOrCreateCircuitBreaker(capability, provider, model);
        cb.transitionToOpenState();

        long start = System.nanoTime();
        boolean permitted = manager.tryAcquirePermission(capability, provider, model);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(permitted).isFalse();
        assertThat(durationMs).isLessThan(5); // Must be strictly < 5 ms
    }

    @Test
    @DisplayName("Scenario 2.4: Non-recoverable client input errors do not penalize circuit breaker")
    void shouldNotPenalizeCircuitOnNonRecoverableErrors() {
        String capability = "chat";
        String provider = "openai";
        String model = "gpt-4o-mini";

        CircuitBreaker cb = manager.getOrCreateCircuitBreaker(capability, provider, model);

        // Record 5 client input errors (INVALID_INPUT)
        for (int i = 0; i < 5; i++) {
            manager.onError(capability, provider, model, 5, new AiProviderException("Bad prompt", AiProviderException.ErrorType.INVALID_INPUT));
        }

        // Circuit should remain CLOSED because non-recoverable errors are ignored
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 2.3: Circuit transitions to HALF_OPEN and closes on successful probe")
    void shouldTransitionToHalfOpenAndCloseOnSuccess() throws InterruptedException {
        String capability = "chat";
        String provider = "gemini";
        String model = "gemini-3.8-flash";

        CircuitBreaker cb = manager.getOrCreateCircuitBreaker(capability, provider, model);
        cb.transitionToOpenState();

        // Wait for open duration (1 second configured in setUp)
        Thread.sleep(1100);

        // State moves to HALF_OPEN upon permission check
        boolean permitted = manager.tryAcquirePermission(capability, provider, model);
        assertThat(permitted).isTrue();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Probe call succeeds
        manager.onSuccess(capability, provider, model, 10);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Inspection: getAllStatuses captures accurate snapshot of circuit metrics")
    void shouldCaptureAccurateSnapshotOfAllStatuses() {
        List<ProviderCircuitStatus> statuses = manager.getAllStatuses();

        assertThat(statuses).isNotEmpty();
        ProviderCircuitStatus geminiStatus = statuses.stream()
                .filter(s -> "chat".equals(s.capability()) && "gemini".equals(s.provider()))
                .findFirst()
                .orElseThrow();

        assertThat(geminiStatus.circuitState()).isEqualTo("CLOSED");
        assertThat(geminiStatus.bufferedCalls()).isEqualTo(0);
    }
}
