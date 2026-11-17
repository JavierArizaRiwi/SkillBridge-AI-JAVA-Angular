package com.riwi.skillbridge.infrastructure.adapter.out.ai.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Records Micrometer metrics for attempts across AI fallback chains.
 */
@Component
public class AiFallbackMetricsRecorder {

    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    public AiFallbackMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public AiFallbackMetricsRecorder() {
        this(null);
    }

    public void recordAttempt(String capability, String provider, String model, boolean success, String errorType, long latencyMs) {
        if (meterRegistry == null) {
            return;
        }

        String status = success ? "SUCCESS" : "FAILURE";
        String effectiveError = errorType != null ? errorType : "none";

        meterRegistry.counter(
                "ai.fallback.attempts",
                "capability", capability != null ? capability : "unknown",
                "provider", provider != null ? provider : "unknown",
                "model", model != null ? model : "unknown",
                "status", status,
                "error_type", effectiveError
        ).increment();

        Timer.builder("ai.fallback.latency")
                .tag("capability", capability != null ? capability : "unknown")
                .tag("provider", provider != null ? provider : "unknown")
                .tag("model", model != null ? model : "unknown")
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, latencyMs)));
    }

    public void recordProviderUsed(String capability, String provider, String model) {
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
                "ai.fallback.provider.used",
                "capability", capability != null ? capability : "unknown",
                "provider", provider != null ? provider : "unknown",
                "model", model != null ? model : "unknown"
        ).increment();
    }
}
