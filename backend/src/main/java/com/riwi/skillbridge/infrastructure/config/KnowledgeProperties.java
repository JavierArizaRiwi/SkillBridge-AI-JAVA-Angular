package com.riwi.skillbridge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for RAG knowledge retrieval and context assembly (HU-26).
 */
@Component
@ConfigurationProperties(prefix = "app.rag")
public class KnowledgeProperties {

    /**
     * Number of top candidates to retrieve from each search strategy (BM25 and vector search).
     */
    private int topK = 3;

    /**
     * Minimum cosine similarity score threshold.
     * Sin valor por defecto inventado; pendiente de calibración con el conjunto de evaluación (FR-019).
     * Se lee de application.yml cuando exista y no interrumpe el arranque si no está establecido.
     */
    private Double minScoreThreshold;

    /**
     * Timeout in seconds for LLM answer generation and embedding requests.
     */
    private int timeoutSeconds = 10;

    /**
     * Maximum character count allowed for the assembled prompt context.
     */
    private int maxContextChars = 6000;

    /**
     * Constant k used in Reciprocal Rank Fusion (RRF) formula.
     */
    private int rrfK = 60;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public Double getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public void setMinScoreThreshold(Double minScoreThreshold) {
        this.minScoreThreshold = minScoreThreshold;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }
}
