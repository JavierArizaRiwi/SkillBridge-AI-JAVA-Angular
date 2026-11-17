package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FallbackAiRecommendationAdapterTest {

    private final AiErrorClassifier errorClassifier = new AiErrorClassifier();
    private AiRecommendationPort primaryAdapter;
    private AiRecommendationPort secondaryAdapter;
    private AiRecommendationPort tertiaryAdapter;

    private FallbackAiRecommendationAdapter fallbackAdapter;

    private static final RecommendationRequest REQ = new RecommendationRequest(InputType.TEXT, "Learn Spring Boot", null, null);
    private static final List<Offering> OFFERINGS = List.of(
            new Offering(UUID.randomUUID(), UUID.randomUUID(), "Spring Mentoring", "Desc", "BACKEND", BigDecimal.valueOf(50), true)
    );
    private static final AiStructuredResponse SUCCESS_RESPONSE = new AiStructuredResponse(
            "Great choices",
            List.of(new AiStructuredResponse.AiItem(UUID.randomUUID().toString(), 0.95, "Matches backend"))
    );

    @BeforeEach
    void setUp() {
        primaryAdapter = mock(AiRecommendationPort.class);
        secondaryAdapter = mock(AiRecommendationPort.class);
        tertiaryAdapter = mock(AiRecommendationPort.class);
    }

    @Test
    @DisplayName("Scenario 1.1: Primary candidate is healthy -> returns result and secondary is never invoked")
    void shouldReturnPrimaryWhenHealthy() {
        when(primaryAdapter.recommend(any(), any())).thenReturn(SUCCESS_RESPONSE);

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        AiStructuredResponse response = fallbackAdapter.recommend(REQ, OFFERINGS);

        assertThat(response.explanation()).isEqualTo(SUCCESS_RESPONSE.explanation());
        assertThat(response.recommendations()).isEqualTo(SUCCESS_RESPONSE.recommendations());
        assertThat(response.resolvedByProvider()).isEqualTo("gemini");
        assertThat(response.resolvedByModel()).isEqualTo("gemini-3.8-flash");

        verify(primaryAdapter, times(1)).recommend(REQ, OFFERINGS);
        verifyNoInteractions(secondaryAdapter);
    }

    @Test
    @DisplayName("Scenario 1.2: Primary candidate fails with recoverable HTTP 429 -> fallback switches to secondary successfully")
    void shouldFallbackToSecondaryOnRecoverableRateLimit() {
        HttpClientErrorException rateLimitEx = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(primaryAdapter.recommend(any(), any())).thenThrow(rateLimitEx);
        when(secondaryAdapter.recommend(any(), any())).thenReturn(SUCCESS_RESPONSE);

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        AiStructuredResponse response = fallbackAdapter.recommend(REQ, OFFERINGS);

        assertThat(response.explanation()).isEqualTo(SUCCESS_RESPONSE.explanation());
        assertThat(response.recommendations()).isEqualTo(SUCCESS_RESPONSE.recommendations());
        assertThat(response.resolvedByProvider()).isEqualTo("openai");
        assertThat(response.resolvedByModel()).isEqualTo("gpt-4o-mini");

        verify(primaryAdapter).recommend(REQ, OFFERINGS);
        verify(secondaryAdapter).recommend(REQ, OFFERINGS);
    }

    @Test
    @DisplayName("Scenario 1.2 (Timeout): Primary fails with SocketTimeoutException -> fallback switches to secondary")
    void shouldFallbackToSecondaryOnTimeout() {
        when(primaryAdapter.recommend(any(), any()))
                .thenThrow(new AiProviderException("Timeout", ErrorType.TIMEOUT, new SocketTimeoutException()));
        when(secondaryAdapter.recommend(any(), any())).thenReturn(SUCCESS_RESPONSE);

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        AiStructuredResponse response = fallbackAdapter.recommend(REQ, OFFERINGS);

        assertThat(response.explanation()).isEqualTo(SUCCESS_RESPONSE.explanation());
        assertThat(response.recommendations()).isEqualTo(SUCCESS_RESPONSE.recommendations());
        assertThat(response.resolvedByProvider()).isEqualTo("openai");
        assertThat(response.resolvedByModel()).isEqualTo("gpt-4o-mini");

        verify(primaryAdapter).recommend(REQ, OFFERINGS);
        verify(secondaryAdapter).recommend(REQ, OFFERINGS);
    }

    @Test
    @DisplayName("Scenario 1.3: Intra-provider fallback evaluated before inter-provider fallback")
    void shouldTryIntraProviderCandidateBeforeInterProvider() {
        when(primaryAdapter.recommend(any(), any()))
                .thenThrow(new AiProviderException("Quota exceeded", ErrorType.RATE_LIMITED));
        when(secondaryAdapter.recommend(any(), any()))
                .thenReturn(SUCCESS_RESPONSE);

        // Chain: Gemini Flash (Primary) -> Gemini Flash-Lite (Intra) -> OpenAI (Inter)
        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash-lite", 2, 10),
                        secondaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 3, 10),
                        tertiaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        AiStructuredResponse response = fallbackAdapter.recommend(REQ, OFFERINGS);

        assertThat(response.explanation()).isEqualTo(SUCCESS_RESPONSE.explanation());
        assertThat(response.recommendations()).isEqualTo(SUCCESS_RESPONSE.recommendations());
        assertThat(response.resolvedByProvider()).isEqualTo("gemini");
        assertThat(response.resolvedByModel()).isEqualTo("gemini-3.8-flash-lite");

        InOrder inOrder = inOrder(primaryAdapter, secondaryAdapter, tertiaryAdapter);
        inOrder.verify(primaryAdapter).recommend(REQ, OFFERINGS);
        inOrder.verify(secondaryAdapter).recommend(REQ, OFFERINGS);
        inOrder.verifyNoMoreInteractions();
        verifyNoInteractions(tertiaryAdapter);
    }

    @Test
    @DisplayName("Scenario 1.4: Non-recoverable client error does NOT trigger fallback and propagates immediately")
    void shouldNotFallbackOnNonRecoverableClientError() {
        when(primaryAdapter.recommend(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid input argument"));

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        assertThatThrownBy(() -> fallbackAdapter.recommend(REQ, OFFERINGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid input argument");

        verify(primaryAdapter).recommend(REQ, OFFERINGS);
        verifyNoInteractions(secondaryAdapter);
    }

    @Test
    @DisplayName("Scenario 3.1: Complete exhaustion of all candidates throws HTTP 503 (AiProviderException UNAVAILABLE) without mock data")
    void shouldThrowUnavailableOnChainExhaustionWithoutMocks() {
        when(primaryAdapter.recommend(any(), any()))
                .thenThrow(new AiProviderException("Gemini server down", ErrorType.UNAVAILABLE));
        when(secondaryAdapter.recommend(any(), any()))
                .thenThrow(new AiProviderException("OpenAI server down", ErrorType.UNAVAILABLE));

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(candidates, errorClassifier);

        assertThatThrownBy(() -> fallbackAdapter.recommend(REQ, OFFERINGS))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException aiEx = (AiProviderException) ex;
                    assertThat(aiEx.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
                });

        verify(primaryAdapter).recommend(REQ, OFFERINGS);
        verify(secondaryAdapter).recommend(REQ, OFFERINGS);
    }

    @Test
    @DisplayName("Edge Case 1: Empty chain with no candidates throws HTTP 503 (UNAVAILABLE) immediately")
    void shouldThrowUnavailableWhenChainIsEmpty() {
        fallbackAdapter = new FallbackAiRecommendationAdapter(List.of(), errorClassifier);

        assertThatThrownBy(() -> fallbackAdapter.recommend(REQ, OFFERINGS))
                .isInstanceOf(AiProviderException.class)
                .satisfies(ex -> {
                    AiProviderException aiEx = (AiProviderException) ex;
                    assertThat(aiEx.getErrorType()).isEqualTo(ErrorType.UNAVAILABLE);
                });
    }

    @Test
    @DisplayName("Scenario 2.2: Primary candidate has OPEN circuit breaker -> skipped immediately without network call and secondary resolves")
    void shouldSkipPrimaryCandidateWhenCircuitIsOpen() {
        com.riwi.skillbridge.infrastructure.config.AiFallbackProperties properties =
                new com.riwi.skillbridge.infrastructure.config.AiFallbackProperties();
        com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager circuitBreakerManager =
                new com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager(properties, errorClassifier);

        // Open circuit for primary candidate (chat:gemini:gemini-3.8-flash)
        circuitBreakerManager.getOrCreateCircuitBreaker("chat", "gemini", "gemini-3.8-flash").transitionToOpenState();

        when(secondaryAdapter.recommend(any(), any())).thenReturn(SUCCESS_RESPONSE);

        List<FallbackAiRecommendationAdapter.CandidateExecutor> candidates = List.of(
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("gemini", "gemini-3.8-flash", 1, 10),
                        primaryAdapter
                ),
                new FallbackAiRecommendationAdapter.CandidateExecutor(
                        AiModelCandidate.of("openai", "gpt-4o-mini", 2, 10),
                        secondaryAdapter
                )
        );

        fallbackAdapter = new FallbackAiRecommendationAdapter(
                candidates,
                errorClassifier,
                new com.riwi.skillbridge.infrastructure.adapter.out.ai.metrics.AiFallbackMetricsRecorder(),
                circuitBreakerManager
        );

        long start = System.currentTimeMillis();
        AiStructuredResponse response = fallbackAdapter.recommend(REQ, OFFERINGS);
        long duration = System.currentTimeMillis() - start;

        assertThat(response.resolvedByProvider()).isEqualTo("openai");
        assertThat(response.resolvedByModel()).isEqualTo("gpt-4o-mini");

        // Verify primary candidate delegate was NEVER invoked over network
        verifyNoInteractions(primaryAdapter);
        verify(secondaryAdapter, times(1)).recommend(REQ, OFFERINGS);
    }
}
