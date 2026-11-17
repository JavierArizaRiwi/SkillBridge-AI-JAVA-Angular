package com.riwi.skillbridge.domain.knowledge;

/**
 * Representa una fuente de conocimiento verificada que acompaña la respuesta generada por el asistente.
 */
public record KnowledgeSource(
        String sourceId,
        String title,
        String snippet,
        double score
) {
    public KnowledgeSource {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId no puede ser nulo ni estar vacío");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title no puede ser nulo ni estar vacío");
        }
        if (snippet == null) {
            throw new IllegalArgumentException("snippet no puede ser nulo");
        }
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score debe estar normalizado en el rango [0.0, 1.0]");
        }
    }
}
