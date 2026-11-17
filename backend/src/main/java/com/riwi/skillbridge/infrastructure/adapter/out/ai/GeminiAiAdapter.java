package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAiAdapter implements AiRecommendationPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiAiAdapter.class);
    private static final String RESPONSE_SCHEMA = """
            {"type":"OBJECT","properties":{"explanation":{"type":"STRING"},"recommendations":{"type":"ARRAY","items":{"type":"OBJECT","properties":{"offeringId":{"type":"STRING"},"score":{"type":"NUMBER"},"reason":{"type":"STRING"}},"required":["offeringId","score","reason"]}}},"required":["explanation","recommendations"]}
            """;
    private final ChatClient chatClient;
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final AiErrorClassifier errorClassifier;

    @Autowired
    public GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties) {
        this(chatClientBuilder, properties, new ObjectMapper(), new AiErrorClassifier());
    }

    public GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties, AiErrorClassifier errorClassifier) {
        this(chatClientBuilder, properties, new ObjectMapper(), errorClassifier);
    }

    GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties, ObjectMapper objectMapper) {
        this(chatClientBuilder, properties, objectMapper, new AiErrorClassifier());
    }

    GeminiAiAdapter(ChatClient.Builder chatClientBuilder, AiProviderProperties properties, ObjectMapper objectMapper, AiErrorClassifier errorClassifier) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.errorClassifier = errorClassifier != null ? errorClassifier : new AiErrorClassifier();
    }

    @Override
    public AiStructuredResponse recommend(RecommendationRequest request, List<Offering> offerings) {
        String catalog = offerings.stream()
                .map(o -> "- ID: %s | Título: %s | Categoría: [%s] | Descripción: %s".formatted(o.id(), o.title(), o.category(), o.description()))
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                Eres el asistente inteligente de SkillBridge AI.
                Tu objetivo es recomendar como máximo 3 servicios del catálogo que ayuden al usuario a lograr su objetivo.
                
                REGLAS IMPORTANTES:
                1. NO inventes servicios que no estén en el catálogo provisto. Solo usa los IDs del catálogo.
                2. Si el objetivo del usuario NO tiene relación alguna con los servicios que ofrece SkillBridge,
                   debes responder con una 'explanation' amable indicando que no ofrecemos servicios para eso, 
                   y dejar la lista de 'recommendations' vacía.
                3. Responde siguiendo exactamente el esquema JSON configurado.
                4. El campo 'score' DEBE ser estrictamente un número decimal (ej. 0.95), NUNCA un string o con comillas extra.
                5. Verifica que todas las llaves y corchetes del JSON estén cerrados correctamente.

                Objetivo del usuario:
                %s

                Catálogo disponible:
                %s
                """.formatted(request.normalizedNeed(), catalog);

        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        try {
            CompletableFuture<AiStructuredResponse> future = CompletableFuture.supplyAsync(() -> execute(prompt));
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new AiProviderException("Gemini timed out after " + timeoutSeconds + "s", ErrorType.TIMEOUT, ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Interrupted during Gemini recommendation", ErrorType.UNAVAILABLE, ex);
            }
        } catch (AiProviderException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException(
                "Gemini is not properly configured: " + ex.getMessage(),
                ErrorType.INVALID_INPUT,
                ex
            );
        } catch (RuntimeException ex) {
            AiErrorClassifier.ClassificationResult classification = errorClassifier.classify(ex);
            ErrorType type = classification.errorType();
            log.error("Gemini recommendation failed type={} correlationId={}", type,
                    CorrelationIdHolder.get(), ex);
            throw new AiProviderException("Gemini recommendation failed: " + type, type, ex);
        }
    }

    private AiStructuredResponse execute(String prompt) {
        String content = chatClient.prompt()
                .user(prompt)
                .options(GoogleGenAiChatOptions.builder()
                        .responseMimeType("application/json")
                        .responseSchema(RESPONSE_SCHEMA)
                        .maxOutputTokens(4096)
                        .build())
                .call()
                .content();
        return parse(content);
    }

    AiStructuredResponse parse(String content) {
        if (content == null || content.isBlank()) {
            throw new AiProviderException("Gemini returned an empty response", ErrorType.INTERNAL_ERROR);
        }
        try {
            AiStructuredResponse response = objectMapper.readValue(content, AiStructuredResponse.class);
            if (response.explanation() == null || response.recommendations() == null) {
                throw new JsonProcessingException("Required response fields are missing") {};
            }
            if (response.recommendations().size() > 3 || response.recommendations().stream().anyMatch(item ->
                    item == null || item.offeringId() == null || item.reason() == null ||
                            !Double.isFinite(item.score()) || item.score() < 0 || item.score() > 1)) {
                throw new JsonProcessingException("Response fields violate recommendation constraints") {};
            }
            return response;
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("Gemini returned invalid or truncated JSON", ErrorType.INTERNAL_ERROR, ex);
        }
    }
}
