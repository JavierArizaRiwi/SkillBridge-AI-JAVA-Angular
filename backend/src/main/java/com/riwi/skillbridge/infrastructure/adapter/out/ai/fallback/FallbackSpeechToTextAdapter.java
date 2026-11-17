package com.riwi.skillbridge.infrastructure.adapter.out.ai.fallback;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.SpeechToTextPort;
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
public class FallbackSpeechToTextAdapter implements SpeechToTextPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackSpeechToTextAdapter.class);

    public record CandidateExecutor(
            AiModelCandidate candidate,
            SpeechToTextPort delegate
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
    public FallbackSpeechToTextAdapter(
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

    public FallbackSpeechToTextAdapter(
            List<CandidateExecutor> candidates,
            AiErrorClassifier errorClassifier
    ) {
        this(candidates, errorClassifier, new AiFallbackMetricsRecorder(), null);
    }

    @Override
    public String transcribe(byte[] audio, String contentType, String fileName) {
        if (candidates.isEmpty()) {
            log.error("No SpeechToText candidates available in fallback chain correlationId={}",
                    CorrelationIdHolder.get());
            throw new AiProviderException("No speech-to-text candidates available or configured", ErrorType.UNAVAILABLE);
        }

        String correlationId = CorrelationIdHolder.get();

        for (CandidateExecutor executor : candidates) {
            AiModelCandidate candidate = executor.candidate();
            long startTime = System.currentTimeMillis();

            if (circuitBreakerManager != null && !circuitBreakerManager.tryAcquirePermission("stt", candidate.providerName(), candidate.modelIdentifier())) {
                log.warn("Circuit Breaker OPEN for STT candidate=[{}] priority={} - skipping network call correlationId={}",
                        candidate.candidateKey(), candidate.priorityOrder(), correlationId);
                metricsRecorder.recordAttempt("stt", candidate.providerName(), candidate.modelIdentifier(), false, "CIRCUIT_OPEN", 0);
                continue;
            }

            log.info("Attempting STT transcription with candidate=[{}] file='{}' correlationId={}",
                    candidate.candidateKey(), fileName, correlationId);

            try {
                String transcription = executor.delegate().transcribe(audio, contentType, fileName);
                long latencyMs = System.currentTimeMillis() - startTime;

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onSuccess("stt", candidate.providerName(), candidate.modelIdentifier(), latencyMs);
                }

                metricsRecorder.recordAttempt("stt", candidate.providerName(), candidate.modelIdentifier(), true, null, latencyMs);
                metricsRecorder.recordProviderUsed("stt", candidate.providerName(), candidate.modelIdentifier());

                log.info("STT transcription succeeded candidate=[{}] latencyMs={} correlationId={}",
                        candidate.candidateKey(), latencyMs, correlationId);

                return transcription;
            } catch (Throwable ex) {
                long latencyMs = System.currentTimeMillis() - startTime;
                AiErrorClassifier.ClassificationResult classification = errorClassifier.classify(ex);

                if (circuitBreakerManager != null) {
                    circuitBreakerManager.onError("stt", candidate.providerName(), candidate.modelIdentifier(), latencyMs, ex);
                }

                metricsRecorder.recordAttempt(
                        "stt",
                        candidate.providerName(),
                        candidate.modelIdentifier(),
                        false,
                        classification.errorType() != null ? classification.errorType().name() : "UNKNOWN",
                        latencyMs
                );

                if (!classification.isRecoverable()) {
                    log.warn("Non-recoverable error on STT candidate=[{}] type={} latencyMs={} correlationId={}",
                            candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
                    if (ex instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new AiProviderException(ex.getMessage(), classification.errorType(), ex);
                }

                log.warn("Recoverable failure on STT candidate=[{}] type={} latencyMs={} - switching correlationId={}",
                        candidate.candidateKey(), classification.errorType(), latencyMs, correlationId);
            }
        }

        log.error("All {} STT candidates exhausted in fallback chain correlationId={}",
                candidates.size(), correlationId);
        throw new AiProviderException("All speech-to-text candidates exhausted", ErrorType.UNAVAILABLE);
    }
}
