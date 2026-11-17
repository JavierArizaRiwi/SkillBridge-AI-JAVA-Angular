package com.riwi.skillbridge.infrastructure.adapter.out.ai.classification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiErrorClassifierTest {

    private final AiErrorClassifier classifier = new AiErrorClassifier();

    @Test
    @DisplayName("SocketTimeoutException is classified as RECOVERABLE TIMEOUT")
    void shouldClassifySocketTimeoutException() {
        SocketTimeoutException error = new SocketTimeoutException("Read timed out");
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.TIMEOUT);
        assertThat(result.isRecoverable()).isTrue();
    }

    @Test
    @DisplayName("Nested TimeoutException inside ExecutionException is classified as RECOVERABLE TIMEOUT")
    void shouldClassifyNestedTimeoutException() {
        ExecutionException error = new ExecutionException(new TimeoutException("Timed out waiting for task"));
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.TIMEOUT);
    }

    @Test
    @DisplayName("ResourceAccessException caused by SocketTimeoutException is classified as RECOVERABLE TIMEOUT")
    void shouldClassifyResourceAccessExceptionTimeout() {
        ResourceAccessException error = new ResourceAccessException("I/O error", new SocketTimeoutException("connect timed out"));
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.TIMEOUT);
    }

    @Test
    @DisplayName("HTTP 429 Too Many Requests is classified as RECOVERABLE RATE_LIMITED")
    void shouldClassifyHttp429() {
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.RATE_LIMITED);
        assertThat(result.isRecoverable()).isTrue();
    }

    @Test
    @DisplayName("HTTP 500, 502, 503, 504 are classified as RECOVERABLE UNAVAILABLE")
    void shouldClassifyHttp5xx() {
        HttpStatus[] serverStatuses = {
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.GATEWAY_TIMEOUT
        };

        for (HttpStatus status : serverStatuses) {
            HttpServerErrorException error = HttpServerErrorException.create(
                    status,
                    status.getReasonPhrase(),
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8
            );
            AiErrorClassifier.ClassificationResult result = classifier.classify(error);

            assertThat(result.classification())
                    .as("Classification for %s", status)
                    .isEqualTo(ErrorClassification.RECOVERABLE);
            assertThat(result.errorType())
                    .as("ErrorType for %s", status)
                    .isEqualTo(ErrorType.UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("Network connectivity errors (ConnectException, UnknownHostException) are classified as RECOVERABLE UNAVAILABLE")
    void shouldClassifyNetworkConnectivityErrors() {
        AiErrorClassifier.ClassificationResult connectResult = classifier.classify(new ConnectException("Connection refused"));
        assertThat(connectResult.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(connectResult.errorType()).isEqualTo(ErrorType.UNAVAILABLE);

        AiErrorClassifier.ClassificationResult hostResult = classifier.classify(new UnknownHostException("api.provider.ai"));
        assertThat(hostResult.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(hostResult.errorType()).isEqualTo(ErrorType.UNAVAILABLE);
    }

    @Test
    @DisplayName("JsonProcessingException from malformed or truncated LLM response is classified as RECOVERABLE INTERNAL_ERROR")
    void shouldClassifyJsonProcessingException() {
        JsonProcessingException error = new JsonProcessingException("Unexpected character") {};
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("HTTP 401/403 at runtime is classified as RECOVERABLE UNAVAILABLE to allow candidate fallback without exposing key")
    void shouldClassifyUpstreamAuthErrorsAsRecoverable() {
        HttpClientErrorException unauthorized = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        AiErrorClassifier.ClassificationResult result = classifier.classify(unauthorized);

        assertThat(result.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.UNAVAILABLE);
    }

    @Test
    @DisplayName("HTTP 400 Bad Request is classified as NON_RECOVERABLE INVALID_INPUT")
    void shouldClassifyHttp400BadRequest() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        AiErrorClassifier.ClassificationResult result = classifier.classify(badRequest);

        assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.INVALID_INPUT);
        assertThat(result.isRecoverable()).isFalse();
    }

    @Test
    @DisplayName("IllegalArgumentException from client validation is classified as NON_RECOVERABLE INVALID_INPUT")
    void shouldClassifyIllegalArgumentException() {
        IllegalArgumentException error = new IllegalArgumentException("User goal cannot be empty");
        AiErrorClassifier.ClassificationResult result = classifier.classify(error);

        assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.INVALID_INPUT);
        assertThat(result.isRecoverable()).isFalse();
    }

    @Test
    @DisplayName("AiProviderException preserves its existing ErrorType and maps to proper classification")
    void shouldClassifyAiProviderException() {
        AiProviderException rateLimitEx = new AiProviderException("Quota exhausted", ErrorType.RATE_LIMITED);
        AiErrorClassifier.ClassificationResult r1 = classifier.classify(rateLimitEx);
        assertThat(r1.classification()).isEqualTo(ErrorClassification.RECOVERABLE);
        assertThat(r1.errorType()).isEqualTo(ErrorType.RATE_LIMITED);

        AiProviderException invalidInputEx = new AiProviderException("Malformed input", ErrorType.INVALID_INPUT);
        AiErrorClassifier.ClassificationResult r2 = classifier.classify(invalidInputEx);
        assertThat(r2.classification()).isEqualTo(ErrorClassification.NON_RECOVERABLE);
        assertThat(r2.errorType()).isEqualTo(ErrorType.INVALID_INPUT);
    }

    @Test
    @DisplayName("Strict typed classification: Message string containing '429 timeout' does NOT fool classification if exception is IllegalArgumentException")
    void shouldNotClassifyBySubstringsInErrorMessage() {
        IllegalArgumentException trickyError = new IllegalArgumentException("Error 429 quota exceeded and timeout occured");
        AiErrorClassifier.ClassificationResult result = classifier.classify(trickyError);

        // Strict rule: must NOT classify as RATE_LIMITED or TIMEOUT just because message contains those words
        assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RECOVERABLE);
        assertThat(result.errorType()).isEqualTo(ErrorType.INVALID_INPUT);
    }
}
