package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import com.riwi.skillbridge.infrastructure.config.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiAiAdapter implements AiRecommendationPort {
    private static final Logger log = LoggerFactory.getLogger(OpenAiAiAdapter.class);

    private final ChatClient chatClient;
    private final AiProviderProperties properties;
    private final AiErrorClassifier errorClassifier;

    @Autowired
    public OpenAiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties) {
        this(chatClientBuilder, properties, new AiErrorClassifier());
    }

    public OpenAiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties, AiErrorClassifier errorClassifier) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.errorClassifier = errorClassifier != null ? errorClassifier : new AiErrorClassifier();
    }

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- ID: %s | Title: %s | Category: [%s] | Description: %s".formatted(o.id(), o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                You are the SkillBridge AI assistant. Your role is to recommend at most 3 services from the catalog
                that align with the user's learning goal. For each recommendation:
                - Explain briefly why this service matches the goal
                - Only recommend services that exist in the catalog below using their exact ID
                
                Do NOT invent services that are not in the catalog.
                If the user's goal is completely unrelated to any catalog offering, provide a polite explanation 
                stating that SkillBridge does not offer those services, and leave recommendations empty.
                
                User's Goal:
                %s
                
                Available Catalog:
                %s
                """.formatted(request.normalizedNeed(), catalog);

        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        try {
            CompletableFuture<AiStructuredResponse> future = CompletableFuture.supplyAsync(() -> {
                AiStructuredResponse response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(AiStructuredResponse.class);

                if (response == null) {
                    throw new AiProviderException("OpenAI did not return a valid structured response", ErrorType.INTERNAL_ERROR);
                }
                if (response.recommendations() == null) {
                    return new AiStructuredResponse(response.explanation(), new ArrayList<>());
                }
                return response;
            });

            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new AiProviderException("OpenAI timed out after " + timeoutSeconds + "s (timeout: " + timeoutSeconds + "s)", ErrorType.TIMEOUT, ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Interrupted during OpenAI recommendation", ErrorType.UNAVAILABLE, ex);
            }
        } catch (AiProviderException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException("OpenAI is not properly configured: " + ex.getMessage(), ErrorType.INVALID_INPUT, ex);
        } catch (RuntimeException ex) {
            AiErrorClassifier.ClassificationResult classification = errorClassifier.classify(ex);
            ErrorType type = classification.errorType();
            log.error("OpenAI recommendation failed type={} correlationId={}", type,
                    CorrelationIdHolder.get(), ex);
            String message = (type == ErrorType.UNAVAILABLE || type == ErrorType.TIMEOUT)
                    ? "OpenAI is temporarily unavailable (timeout: " + timeoutSeconds + "s)"
                    : "OpenAI recommendation failed: " + type;
            throw new AiProviderException(message, type, ex);
        }
    }
}
