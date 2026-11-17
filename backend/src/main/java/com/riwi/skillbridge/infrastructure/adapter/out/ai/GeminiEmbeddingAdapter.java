package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.ErrorType;
import com.riwi.skillbridge.infrastructure.config.KnowledgeProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Adaptador de embeddings de Gemini detrás de EmbeddingPort (HU-26).
 *
 * Utiliza el EmbeddingModel inyectado de Spring AI configurado con gemini-embedding-001 y 768 dimensiones.
 * Aplica timeout estricto en cliente mediante Future.get(timeout) leyendo app.rag.timeout-seconds (FR-008).
 * Si no hay clave configurada o la llamada falla, lanza AiProviderException con código UNAVAILABLE o TIMEOUT (FR-017).
 * Nunca devuelve vectores simulados (FR-009).
 */
@Component
public class GeminiEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingAdapter.class);

    private final EmbeddingModel embeddingModel;
    private final KnowledgeProperties properties;
    private final String apiKey;
    private final ExecutorService executor;

    /**
     * Constructor de Spring: elige el modelo de Google entre los {@link EmbeddingModel} disponibles
     * (puede haber otros, p. ej. OpenAI) y tolera que no exista ninguno (sin clave: 503 al usarse).
     */
    @Autowired
    public GeminiEmbeddingAdapter(
            ObjectProvider<EmbeddingModel> embeddingModels,
            KnowledgeProperties properties,
            @Value("${spring.ai.google.genai.embedding.api-key:}") String apiKey
    ) {
        this(embeddingModels.orderedStream()
                        .filter(m -> m instanceof GoogleGenAiTextEmbeddingModel)
                        .findFirst()
                        .orElse(null),
                properties, apiKey, (ExecutorService) null);
    }

    public GeminiEmbeddingAdapter(EmbeddingModel embeddingModel, KnowledgeProperties properties, String apiKey) {
        this(embeddingModel, properties, apiKey, (ExecutorService) null);
    }

    public GeminiEmbeddingAdapter(EmbeddingModel embeddingModel, KnowledgeProperties properties) {
        this(embeddingModel, properties, "configured-key", (ExecutorService) null);
    }

    public GeminiEmbeddingAdapter(
            EmbeddingModel embeddingModel,
            KnowledgeProperties properties,
            String apiKey,
            ExecutorService executor
    ) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.apiKey = apiKey;
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        if (apiKey == null || apiKey.isBlank() || embeddingModel == null) {
            log.error("Gemini embedding request rejected: missing API key or unconfigured EmbeddingModel");
            throw new AiProviderException(
                    "Gemini embedding API key is not configured or provider unavailable",
                    ErrorType.UNAVAILABLE
            );
        }

        int timeoutSeconds = (properties != null && properties.getTimeoutSeconds() > 0)
                ? properties.getTimeoutSeconds()
                : 10;

        Future<List<float[]>> future = executor.submit(() -> embeddingModel.embed(texts));

        List<float[]> rawEmbeddings;
        try {
            rawEmbeddings = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Gemini embedding request timed out after {}s", timeoutSeconds, e);
            throw new AiProviderException(
                    "Gemini embedding request timed out after " + timeoutSeconds + "s",
                    ErrorType.TIMEOUT,
                    e
            );
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.error("Gemini embedding request interrupted", e);
            throw new AiProviderException(
                    "Gemini embedding request was interrupted",
                    ErrorType.TIMEOUT,
                    e
            );
        } catch (ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof AiProviderException ape) {
                throw ape;
            }
            ErrorType type = classify(cause);
            log.error("Gemini embedding request failed with type={}: {}", type, cause.getMessage(), cause);
            throw new AiProviderException("Gemini embedding request failed: " + type.getDescription(), type, cause);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected error in Gemini embedding", e);
            ErrorType type = classify(e);
            throw new AiProviderException("Gemini embedding request failed: " + type.getDescription(), type, e);
        }

        if (rawEmbeddings == null || rawEmbeddings.size() != texts.size()) {
            log.error("Gemini embedding provider returned invalid result count: expected={}, actual={}",
                    texts.size(), rawEmbeddings == null ? 0 : rawEmbeddings.size());
            throw new AiProviderException(
                    "Gemini embedding provider returned invalid response",
                    ErrorType.UNAVAILABLE
            );
        }

        List<double[]> result = new ArrayList<>(rawEmbeddings.size());
        for (float[] vector : rawEmbeddings) {
            if (vector == null) {
                throw new AiProviderException("Gemini embedding provider returned null vector", ErrorType.UNAVAILABLE);
            }
            double[] d = new double[vector.length];
            for (int i = 0; i < vector.length; i++) {
                d[i] = (double) vector[i];
            }
            result.add(d);
        }

        return Collections.unmodifiableList(result);
    }

    private ErrorType classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String type = cause.getClass().getSimpleName().toLowerCase();
            String message = String.valueOf(cause.getMessage()).toLowerCase();
            if (type.contains("timeout") || message.contains("timed out") || message.contains("timeout")) {
                return ErrorType.TIMEOUT;
            }
            if (type.contains("connect") || message.contains("connection") || message.contains("unreachable")) {
                return ErrorType.UNAVAILABLE;
            }
        }
        return ErrorType.UNAVAILABLE;
    }

    @PreDestroy
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
