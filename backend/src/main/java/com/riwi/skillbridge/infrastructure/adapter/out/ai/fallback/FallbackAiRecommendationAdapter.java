package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate.AiModelCandidate;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.metrics.AiFallbackMetricsRecorder;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite fallback adapter for AI recommendation capabilities.
 *
 * Implements {@link AiRecommendationPort} and orchestrates an ordered chain of candidate models,
 * switching only on recoverable errors (rate limits, timeouts, 5xx, network failures)
 * and propagating non-recoverable client input errors immediately without fallback.
 */
@Component
@Primary
public class FallbackAiRecommendationAdapter implements AiRecommendationPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackAiRecommendationAdapter.class);

    public record CandidateExecutor(
            AiModelCandidate candidate,
            AiRecommendationPort delegate
    ) {
        public CandidateExecutor {
            Objects.requireNonNull(candidate, "candidate must not be null");
            Objects.requireNonNull(delegate, "delegate must not be null");
        }
    }

    private final List<CandidateExecutor> candidates;
    private final AiErrorClassifier errorClassifier;
    private final AiFallbackMetricsRecorder metricsRecorder;
    private final AiCircuitBreakerManager circuitBreakerManager;

    @Autowired
    public FallbackAiRecommendationAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier,
            AiFallbackMetricsRecorder metricsRecorder,
            @Autowired(required = false) AiCircuitBreakerManager circuitBreakerManager
    ) {
        this.candidates = candidates != null ? new ArrayList<>(candidates) : Collections.emptyList();
        this.errorClassifier = errorClassifier != null ? errorClassifier : new AiErrorClassifier();
        this.metricsRecorder = metricsRecorder != null ? metricsRecorder : new AiFallbackMetricsRecorder();
        this.circuitBreakerManager = circuitBreakerManager;
    }

    public FallbackAiRecommendationAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier,
            AiFallbackMetricsRecorder metricsRecorder
    ) {
        this(candidates, errorClassifier, metricsRecorder, null);
    }

    public FallbackAiRecommendationAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier
    ) {
        this(candidates, errorClassifier, new AiFallbackMetricsRecorder(), null);
    }

    public FallbackAiRecommendationAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier,
            AiCircuitBreakerManager circuitBreakerManager
    ) {
        this(candidates, errorClassifier, new AiFallbackMetricsRecorder(), circuitBreakerManager);
    }

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        if (candidates.isEmpty()) {
            log.error("No AI recommendation candidates available or configured in fallback chain correlationId={}",
                    CorrelationIdHolder.get());
            throw new AiProviderException("No AI recommendation candidates available or configured", ErrorType.UNAVAILABLE);
        }

        String correlationId = CorrelationIdHolder.get();

        for (int i = 0; i < candidates.size(); i++) {
            CandidateExecutor executor = candidates.get(i);
            AiModelCandidate candidate = executor.candidate();
            long startTime = System.currentTimeMillis();

            if (circuitBreakerManager != null && !circuitBreakerManager.tryAcquirePermission("chat", candidate.providerName(), candidate.modelIdentifier())) {
                log.warn("Circuit Breaker OPEN for candidate=[{}] priority={} - skipping network call correlationId={}",
                        candidate.candidateKey(), candidate.priorityOrder(), correlationId);
                metricsRecorder.recordAttempt("recommendation", candidate.providerName(), candidate.modelIdentifier(), false, "CIRCUIT_OPEN", 0);
                continue;
            }

            log.info("Attempting AI recommendation candidate=[{}] priority={} correlationId={}",
                    candidate.candidateKey(), candidate.priorityOrder(), correlationId);

            try {
                AiStructuredResponse response = executor.delegate().recommend(request, offerings);
                long latencyMs = System.currentTimeMillis() - startTime;

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onSuccess("chat", candidate.providerName(), candidate.modelIdentifier(), latencyMs);
                }

                metricsRecorder.recordAttempt("recommendation", candidate.providerName(), candidate.modelIdentifier(), true, null, latencyMs);
                metricsRecorder.recordProviderUsed("recommendation", candidate.providerName(), candidate.modelIdentifier());

                log.info("AI recommendation succeeded with candidate=[{}] latencyMs={} correlationId={}",
                        candidate.candidateKey(), latencyMs, correlationId);

                // Enrich with candidate identity
                return new AiStructuredResponse(
                        response.explanation(),
                        response.recommendations(),
                        candidate.providerName(),
                        candidate.modelIdentifier()
                );
            } catch (Throwable ex) {
                long latencyMs = System.currentTimeMillis() - startTime;
                AiErrorClassifier.ClassificationResult classification = errorClassifier.classify(ex);

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onError("chat", candidate.providerName(), candidate.modelIdentifier(), latencyMs, ex);
                }

                metricsRecorder.recordAttempt(
                        "recommendation",
                        candidate.providerName(),
                        candidate.modelIdentifier(),
                        false,
                        classification.errorType() != null ? classification.errorType().name() : "UNKNOWN",
                        latencyMs
                );

                if (!classification.isRecoverable()) {
                    log.warn("Non-recoverable error on AI candidate=[{}] type={} latencyMs={} - aborting fallback correlationId={}",
                            candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
                    if (ex instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new AiProviderException(ex.getMessage(), classification.errorType(), ex);
                }

                log.warn("Recoverable failure on candidate=[{}] errorType={} latencyMs={} - switching to next candidate correlationId={}",
                        candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
            }
        }

        log.error("All {} AI recommendation candidates exhausted in fallback chain correlationId={}",
                candidates.size(), correlationId);
        throw new AiProviderException("All AI providers in fallback chain exhausted", ErrorType.UNAVAILABLE);
    }

    public List<CandidateExecutor> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }
}
