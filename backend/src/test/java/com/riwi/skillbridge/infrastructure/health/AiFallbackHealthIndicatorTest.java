package com.riwi.skillbridge.infrastructure.health;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.ProviderCircuitStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFallbackHealthIndicatorTest {

    private final AiCircuitBreakerManager manager = mock(AiCircuitBreakerManager.class);
    private final AiFallbackHealthIndicator healthIndicator = new AiFallbackHealthIndicator(manager);

    @Test
    @DisplayName("Health is UP when all circuits are CLOSED")
    void shouldReportUpWhenAllCircuitsClosed() {
        when(manager.getAllStatuses()).thenReturn(List.of(
                new ProviderCircuitStatus("chat", "gemini", "gemini-3.8-flash", "CLOSED", 0f, 0, 0, 0),
                new ProviderCircuitStatus("chat", "openai", "gpt-4o-mini", "CLOSED", 0f, 0, 0, 0)
        ));

        Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("openCircuits", 0);
    }

    @Test
    @DisplayName("Health is DEGRADED when some candidate circuit is OPEN but others are operational")
    void shouldReportDegradedWhenOneCandidateIsOpen() {
        when(manager.getAllStatuses()).thenReturn(List.of(
                new ProviderCircuitStatus("chat", "gemini", "gemini-3.8-flash", "OPEN", 100f, 10, 10, 5),
                new ProviderCircuitStatus("chat", "openai", "gpt-4o-mini", "CLOSED", 0f, 0, 0, 0)
        ));

        Health health = healthIndicator.health();
        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("openCircuits", 1L);
    }

    @Test
    @DisplayName("Health is DOWN when all candidates in a capability are OPEN")
    void shouldReportDownWhenAllCandidatesInCapabilityAreOpen() {
        when(manager.getAllStatuses()).thenReturn(List.of(
                new ProviderCircuitStatus("chat", "gemini", "gemini-3.8-flash", "OPEN", 100f, 10, 10, 5),
                new ProviderCircuitStatus("chat", "openai", "gpt-4o-mini", "OPEN", 100f, 10, 10, 5)
        ));

        Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("openCircuits", 2L);
    }
}
