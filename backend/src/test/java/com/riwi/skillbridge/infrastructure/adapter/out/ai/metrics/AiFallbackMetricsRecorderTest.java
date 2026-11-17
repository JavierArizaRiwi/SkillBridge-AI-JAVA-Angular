package com.riwi.skillbridge.infrastructure.adapter.out.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AiFallbackMetricsRecorderTest {

    @Test
    @DisplayName("Successfully records attempts and latency counters in MeterRegistry")
    void shouldRecordAttemptsAndLatencyInRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        AiFallbackMetricsRecorder recorder = new AiFallbackMetricsRecorder(registry);

        recorder.recordAttempt("recommendation", "gemini", "gemini-3.8-flash", true, null, 150);
        recorder.recordAttempt("recommendation", "gemini", "gemini-3.8-flash", false, "RATE_LIMITED", 300);

        Counter successCounter = registry.find("ai.fallback.attempts")
                .tag("capability", "recommendation")
                .tag("provider", "gemini")
                .tag("model", "gemini-3.8-flash")
                .tag("status", "SUCCESS")
                .counter();

        Counter failureCounter = registry.find("ai.fallback.attempts")
                .tag("capability", "recommendation")
                .tag("provider", "gemini")
                .tag("model", "gemini-3.8-flash")
                .tag("status", "FAILURE")
                .tag("error_type", "RATE_LIMITED")
                .counter();

        Timer successTimer = registry.find("ai.fallback.latency")
                .tag("capability", "recommendation")
                .tag("provider", "gemini")
                .tag("model", "gemini-3.8-flash")
                .tag("status", "SUCCESS")
                .timer();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);

        assertThat(successTimer).isNotNull();
        assertThat(successTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(150.0);
    }

    @Test
    @DisplayName("Handles null MeterRegistry safely as a NoOp")
    void shouldHandleNullRegistryGracefully() {
        AiFallbackMetricsRecorder recorder = new AiFallbackMetricsRecorder(null);
        // Should not throw any exception
        recorder.recordAttempt("stt", "groq", "whisper", false, "TIMEOUT", 200);
    }
}
