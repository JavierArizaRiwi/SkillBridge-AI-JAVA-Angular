package com.riwi.skillbridge.domain.knowledge;

import java.util.List;

/**
 * Representa la respuesta consolidada del caso de uso de consulta de conocimiento (RAG).
 */
public record KnowledgeAnswer(
        String answer,
        boolean hasSufficientContext,
        List<KnowledgeSource> sources
) {
    public KnowledgeAnswer {
        if (answer == null) {
            throw new IllegalArgumentException("answer no puede ser nulo");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public KnowledgeAnswer(String answer, boolean hasSufficientContext) {
        this(answer, hasSufficientContext, List.of());
    }
}
