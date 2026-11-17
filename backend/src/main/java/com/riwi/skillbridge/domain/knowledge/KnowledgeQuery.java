package com.riwi.skillbridge.domain.knowledge;

import java.time.Instant;

/**
 * Representa una consulta formulada al asistente de conocimiento (RAG).
 */
public record KnowledgeQuery(
        String question,
        Instant askedAt,
        String userId
) {
    public static final int MAX_QUESTION_LENGTH = 500;

    public KnowledgeQuery {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La pregunta no puede ser nula ni estar vacía");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("La pregunta no puede superar los " + MAX_QUESTION_LENGTH + " caracteres");
        }
    }

    public KnowledgeQuery(String question) {
        this(question, Instant.now(), null);
    }
}
