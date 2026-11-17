package com.riwi.skillbridge.infrastructure.adapter.out.ai.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Strict typed error classifier for AI providers and candidates.
 *
 * Adheres strictly to ADR-0007:
 * Classifies by exception type and HTTP status code rather than loose substring heuristics.
 */
@Component
public class AiErrorClassifier {

    public record ClassificationResult(
            ErrorClassification classification,
            ErrorType errorType,
            String message
    ) {
        public boolean isRecoverable() {
            return classification == ErrorClassification.RECOVERABLE;
        }
    }

    /**
     * Inspects the exception hierarchy and returns an error classification and domain ErrorType.
     *
     * @param error the throwable to inspect
     * @return typed classification result
     */
    public ClassificationResult classify(Throwable error) {
        Objects.requireNonNull(error, "error must not be null");

        // Inspect the cause chain from top to root
        for (Throwable current = error; current != null; current = current.getCause()) {
            // 1. Explicit domain AiProviderException
            if (current instanceof AiProviderException aiEx) {
                ErrorClassification classification = switch (aiEx.getErrorType()) {
                    case INVALID_INPUT -> ErrorClassification.NON_RECOVERABLE;
                    case TIMEOUT, UNAVAILABLE, RATE_LIMITED, INTERNAL_ERROR -> ErrorClassification.RECOVERABLE;
                };
                return new ClassificationResult(classification, aiEx.getErrorType(), aiEx.getMessage());
            }

            // 2. HTTP Status Code exceptions from Spring Web (RestClient / RestTemplate / WebClient)
            if (current instanceof HttpStatusCodeException httpEx) {
                int status = httpEx.getStatusCode().value();
                return classifyHttpStatus(status, httpEx.getMessage());
            }

            // 3. Client input / argument validation errors are strictly NON_RECOVERABLE
            if (current instanceof IllegalArgumentException) {
                return new ClassificationResult(ErrorClassification.NON_RECOVERABLE, ErrorType.INVALID_INPUT, current.getMessage());
            }

            // 4. Timeout exceptions (typed + socket/future + timeout indicator)
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().toLowerCase().contains("timeout")
                    || (current.getMessage() != null && (current.getMessage().toLowerCase().contains("timed out") || current.getMessage().toLowerCase().contains("timeout")))) {
                return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.TIMEOUT, current.getMessage());
            }

            // 5. Connectivity / Network exceptions
            if (current instanceof ConnectException || current instanceof UnknownHostException || current instanceof NoRouteToHostException) {
                return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.UNAVAILABLE, current.getMessage());
            }

            // 6. JSON parsing failures from LLM outputs (empty, truncated, or invalid schema from provider)
            if (current instanceof JsonProcessingException) {
                return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.INTERNAL_ERROR, current.getMessage());
            }
        }

        // Generic fallback: unclassified runtime exceptions default to RECOVERABLE UNAVAILABLE
        // to allow backup candidates to attempt resolution
        return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.UNAVAILABLE, error.getMessage());
    }

    private ClassificationResult classifyHttpStatus(int status, String message) {
        if (status == 429) {
            return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.RATE_LIMITED, message);
        }
        if (status >= 500 && status <= 599) {
            return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.UNAVAILABLE, message);
        }
        if (status == 401 || status == 403) {
            // Upstream authentication failure at runtime -> treat candidate as unavailable without failing client non-recoverably
            return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.UNAVAILABLE, message);
        }
        if (status >= 400 && status <= 499) {
            return new ClassificationResult(ErrorClassification.NON_RECOVERABLE, ErrorType.INVALID_INPUT, message);
        }
        return new ClassificationResult(ErrorClassification.RECOVERABLE, ErrorType.UNAVAILABLE, message);
    }
}
