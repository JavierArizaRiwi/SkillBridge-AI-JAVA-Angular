package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.KnowledgeAnswerPort;
import com.riwi.skillbridge.application.service.knowledge.KnowledgePromptBuilder;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import com.riwi.skillbridge.infrastructure.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Adaptador de salida para la generación de respuestas de conocimiento utilizando Google Gemini
 * mediante Spring AI ChatClient (HU-26, FR-008, FR-009, FR-010).
 * <p>
 * Implementa {@link KnowledgeAnswerPort} aplicando timeout real en cliente con cancelación activa
 * (FR-008, SC-006), clasificación de errores de proveedor (FR-009) y registro de auditoría seguro
 * sin volcado del texto de preguntas ni credenciales (FR-010).
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiKnowledgeAnswerAdapter implements KnowledgeAnswerPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GeminiKnowledgeAnswerAdapter.class);

    private final ChatClient chatClient;
    private final KnowledgeProperties properties;
    private final KnowledgePromptBuilder promptBuilder;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;

    @Autowired
    public GeminiKnowledgeAnswerAdapter(ChatClient.Builder chatClientBuilder,
                                        KnowledgeProperties properties) {
        this(chatClientBuilder.build(), properties,
                new KnowledgePromptBuilder(resolveMaxContextChars(properties)));
    }

    public GeminiKnowledgeAnswerAdapter(ChatClient chatClient,
                                        KnowledgeProperties properties) {
        this(chatClient, properties,
                new KnowledgePromptBuilder(resolveMaxContextChars(properties)));
    }

    public GeminiKnowledgeAnswerAdapter(ChatClient.Builder chatClientBuilder,
                                        KnowledgeProperties properties,
                                        KnowledgePromptBuilder promptBuilder) {
        this(chatClientBuilder.build(), properties, promptBuilder);
    }

    public GeminiKnowledgeAnswerAdapter(ChatClient chatClient,
                                        KnowledgeProperties properties,
                                        KnowledgePromptBuilder promptBuilder) {
        this(chatClient, properties, promptBuilder, createDefaultExecutor(), true);
    }

    public GeminiKnowledgeAnswerAdapter(ChatClient chatClient,
                                        KnowledgeProperties properties,
                                        KnowledgePromptBuilder promptBuilder,
                                        ExecutorService executorService) {
        this(chatClient, properties, promptBuilder, executorService, false);
    }

    private GeminiKnowledgeAnswerAdapter(ChatClient chatClient,
                                         KnowledgeProperties properties,
                                         KnowledgePromptBuilder promptBuilder,
                                         ExecutorService executorService,
                                         boolean ownsExecutor) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient no puede ser nulo");
        this.properties = Objects.requireNonNull(properties, "properties no puede ser nulo");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder no puede ser nulo");
        this.executorService = Objects.requireNonNull(executorService, "executorService no puede ser nulo");
        this.ownsExecutor = ownsExecutor;
    }

    private static int resolveMaxContextChars(KnowledgeProperties properties) {
        if (properties == null || properties.getMaxContextChars() <= 0) {
            return KnowledgePromptBuilder.DEFAULT_MAX_CONTEXT_CHARS;
        }
        return properties.getMaxContextChars();
    }

    private static ExecutorService createDefaultExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "gemini-knowledge-answer");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public String generateAnswer(KnowledgeQuery query, List<KnowledgeChunk> validatedChunks) {
        if (query == null) {
            throw new IllegalArgumentException("La consulta no puede ser nula");
        }
        return generateAnswer(query.question(), validatedChunks);
    }

    @Override
    public String generateAnswer(String question, List<KnowledgeChunk> validatedChunks) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula ni estar vacía");
        }

        String correlationId = CorrelationIdHolder.get();
        int chunkCount = validatedChunks != null ? validatedChunks.size() : 0;
        int timeoutSeconds = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 10;

        String prompt = promptBuilder.buildPrompt(question, validatedChunks);

        log.info("Iniciando generación de respuesta de conocimiento: correlationId={}, chunkCount={}",
                correlationId, chunkCount);

        Future<String> future;
        try {
            future = executorService.submit(() -> {
                String originalCid = CorrelationIdHolder.get();
                try {
                    if (correlationId != null) {
                        CorrelationIdHolder.set(correlationId);
                    }
                    return chatClient.prompt()
                            .user(prompt)
                            .call()
                            .content();
                } finally {
                    if (correlationId != null) {
                        CorrelationIdHolder.clear();
                    }
                    if (originalCid != null) {
                        CorrelationIdHolder.set(originalCid);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Ejecución de respuesta de conocimiento rechazada: correlationId={}, chunkCount={}, errorType={}",
                    correlationId, chunkCount, ErrorType.UNAVAILABLE);
            throw new AiProviderException("Gemini knowledge answer executor unavailable", ErrorType.UNAVAILABLE, e);
        }

        try {
            String response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (response == null || response.isBlank()) {
                log.error("Gemini devolvió respuesta vacía: correlationId={}, chunkCount={}, errorType={}",
                        correlationId, chunkCount, ErrorType.UNAVAILABLE);
                throw new AiProviderException("Gemini returned an empty response", ErrorType.UNAVAILABLE);
            }
            log.info("Respuesta de conocimiento generada exitosamente: correlationId={}, chunkCount={}",
                    correlationId, chunkCount);
            return response.trim();
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Llamada a Gemini expiró por timeout ({}s): correlationId={}, chunkCount={}, errorType={}",
                    timeoutSeconds, correlationId, chunkCount, ErrorType.TIMEOUT);
            throw new AiProviderException("Gemini knowledge answer timed out after " + timeoutSeconds + " seconds",
                    ErrorType.TIMEOUT, e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.error("Llamada a Gemini interrumpida: correlationId={}, chunkCount={}, errorType={}",
                    correlationId, chunkCount, ErrorType.UNAVAILABLE);
            throw new AiProviderException("Gemini knowledge answer was interrupted", ErrorType.UNAVAILABLE, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw handleExecutionFailure(cause, correlationId, chunkCount);
        }
    }

    private AiProviderException handleExecutionFailure(Throwable cause, String correlationId, int chunkCount) {
        if (cause instanceof AiProviderException ape) {
            log.error("Fallo en proveedor Gemini: correlationId={}, chunkCount={}, errorType={}",
                    correlationId, chunkCount, ape.getErrorType());
            return ape;
        }

        ErrorType errorType = classify(cause);
        log.error("Fallo en generación de respuesta con Gemini: correlationId={}, chunkCount={}, errorType={}",
                correlationId, chunkCount, errorType);
        return new AiProviderException("Gemini knowledge answer failed: " + errorType, errorType, cause);
    }

    private ErrorType classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderException ape) {
                return ape.getErrorType();
            }
            String type = cause.getClass().getSimpleName().toLowerCase();
            String message = String.valueOf(cause.getMessage()).toLowerCase();

            if (type.contains("timeout") || message.contains("timed out") || message.contains("timeout")) {
                return ErrorType.TIMEOUT;
            }
            if (message.contains("429") || message.contains("quota") || message.contains("rate limit") || message.contains("rate_limit") || message.contains("too many requests")) {
                return ErrorType.RATE_LIMITED;
            }
            if (message.contains("401") || message.contains("403") || message.contains("unauthorized") || message.contains("api key")) {
                return ErrorType.INVALID_INPUT;
            }
            if (message.contains("404") || (message.contains("model") && message.contains("not found"))) {
                return ErrorType.INVALID_INPUT;
            }
            if (type.contains("connect") || message.contains("connection") || message.contains("unreachable")) {
                return ErrorType.UNAVAILABLE;
            }
        }
        return ErrorType.UNAVAILABLE;
    }

    @Override
    public void destroy() {
        if (ownsExecutor) {
            executorService.shutdown();
        }
    }
}
