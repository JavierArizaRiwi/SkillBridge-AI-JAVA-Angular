package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import java.util.Objects;

/**
 * Representa un candidato recuperado por el motor de búsqueda de conocimiento
 * junto con su puntuación de similitud coseno respecto a la consulta.
 */
public record KnowledgeCandidate(
        KnowledgeChunk chunk,
        double cosineSimilarity
) {
    public KnowledgeCandidate {
        Objects.requireNonNull(chunk, "chunk no puede ser nulo");
        if (Double.isNaN(cosineSimilarity)) {
            throw new IllegalArgumentException("cosineSimilarity no puede ser NaN");
        }
    }

    public double score() {
        return cosineSimilarity;
    }
}
