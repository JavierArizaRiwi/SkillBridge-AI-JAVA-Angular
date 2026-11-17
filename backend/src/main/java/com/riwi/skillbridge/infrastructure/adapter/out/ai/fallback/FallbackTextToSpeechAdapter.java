package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.TextToSpeechPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
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

@Component
@Primary
public class FallbackTextToSpeechAdapter implements TextToSpeechPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackTextToSpeechAdapter.class);

    public record CandidateExecutor(
            AiModelCandidate candidate,
            TextToSpeechPort delegate
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
    public FallbackTextToSpeechAdapter(
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

    public FallbackTextToSpeechAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier
    ) {
        this(candidates, errorClassifier, new AiFallbackMetricsRecorder(), null);
    }

    @Override
    public AudioContent synthesize(String text) {
        if (candidates.isEmpty()) {
            log.error("No TextToSpeech candidates available in fallback chain correlationId={}",
                    CorrelationIdHolder.get());
            throw new AiProviderException("No text-to-speech candidates available or configured", ErrorType.UNAVAILABLE);
        }

        String correlationId = CorrelationIdHolder.get();

        for (CandidateExecutor executor : candidates) {
            AiModelCandidate candidate = executor.candidate();
            long startTime = System.currentTimeMillis();

            if (circuitBreakerManager != null && !circuitBreakerManager.tryAcquirePermission("tts", candidate.providerName(), candidate.modelIdentifier())) {
                log.warn("Circuit Breaker OPEN for TTS candidate=[{}] priority={} - skipping network call correlationId={}",
                        candidate.candidateKey(), candidate.priorityOrder(), correlationId);
                metricsRecorder.recordAttempt("tts", candidate.providerName(), candidate.modelIdentifier(), false, "CIRCUIT_OPEN", 0);
                continue;
            }

            log.info("Attempting TTS synthesis candidate=[{}] textLength={} correlationId={}",
                    candidate.candidateKey(), text != null ? text.length() : 0, correlationId);

            try {
                AudioContent audio = executor.delegate().synthesize(text);
                long latencyMs = System.currentTimeMillis() - startTime;

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onSuccess("tts", candidate.providerName(), candidate.modelIdentifier(), latencyMs);
                }

                metricsRecorder.recordAttempt("tts", candidate.providerName(), candidate.modelIdentifier(), true, null, latencyMs);
                metricsRecorder.recordProviderUsed("tts", candidate.providerName(), candidate.modelIdentifier());

                log.info("TTS synthesis succeeded candidate=[{}] bytes={} latencyMs={} correlationId={}",
                        candidate.candidateKey(), audio.bytes() != null ? audio.bytes().length : 0, latencyMs, correlationId);

                return audio;
            } catch (Throwable ex) {
                long latencyMs = System.currentTimeMillis() - startTime;
                AiErrorClassifier.ClassificationResult classification = errorClassifier.classify(ex);

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onError("tts", candidate.providerName(), candidate.modelIdentifier(), latencyMs, ex);
                }

                metricsRecorder.recordAttempt(
                        "tts",
                        candidate.providerName(),
                        candidate.modelIdentifier(),
                        false,
                        classification.errorType() != null ? classification.errorType().name() : "UNKNOWN",
                        latencyMs
                );

                if (!classification.isRecoverable()) {
                    log.warn("Non-recoverable error on TTS candidate=[{}] type={} latencyMs={} correlationId={}",
                            candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
                    if (ex instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new AiProviderException(ex.getMessage(), classification.errorType(), ex);
                }

                log.warn("Recoverable failure on TTS candidate=[{}] type={} latencyMs={} - switching correlationId={}",
                        candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
            }
        }

        log.error("All {} TTS candidates exhausted in fallback chain correlationId={}",
                candidates.size(), correlationId);
        throw new AiProviderException("All text-to-speech candidates exhausted", ErrorType.UNAVAILABLE);
    }
}
