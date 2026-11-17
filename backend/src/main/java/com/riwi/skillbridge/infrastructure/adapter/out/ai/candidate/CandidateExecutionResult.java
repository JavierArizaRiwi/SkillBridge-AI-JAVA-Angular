package com.riwi.skillbridge.infrastructure.adapter.out.ai.candidate;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.ErrorClassification;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of executing an individual candidate in the AI fallback chain.
 */
public record CandidateExecutionResult(
        AiModelCandidate candidate,
        Outcome outcome,
        ErrorClassification errorClassification,
        AiProviderException.ErrorType errorType,
        String errorMessage,
        long latencyMs
) {
    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    public CandidateExecutionResult {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (latencyMs < 0) {
            latencyMs = 0;
        }
    }

    public static CandidateExecutionResult success(AiModelCandidate candidate, long latencyMs) {
        return new CandidateExecutionResult(
                candidate,
                Outcome.SUCCESS,
                null,
                null,
                null,
                latencyMs
        );
    }

    public static CandidateExecutionResult failure(
            AiModelCandidate candidate,
            ErrorClassification errorClassification,
            AiProviderException.ErrorType errorType,
            String errorMessage,
            long latencyMs
    ) {
        return new CandidateExecutionResult(
                candidate,
                Outcome.FAILURE,
                Objects.requireNonNull(errorClassification, "errorClassification must not be null on failure"),
                errorType,
                errorMessage,
                latencyMs
        );
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean isRecoverable() {
        return errorClassification != null && errorClassification.isRecoverable();
    }
}
