package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import java.util.List;

/**
 * Puerto de salida para la búsqueda híbrida de conocimiento (BM25 + embeddings fusionados con RRF).
 */
public interface KnowledgeSearchPort {

    /**
     * Realiza una búsqueda híbrida y devuelve los fragmentos candidatos ordenados
     * junto con su puntuación de similitud coseno.
     *
     * @param query texto de la consulta
     * @param topK  número máximo de candidatos a recuperar
     * @return lista de candidatos con su similitud coseno
     */
    List<KnowledgeCandidate> search(String query, int topK);

    /**
     * Sobrecarga que acepta un objeto de dominio KnowledgeQuery.
     *
     * @param query objeto de consulta
     * @param topK  número máximo de candidatos a recuperar
     * @return lista de candidatos con su similitud coseno
     */
    default List<KnowledgeCandidate> search(KnowledgeQuery query, int topK) {
        if (query == null) {
            throw new IllegalArgumentException("query no puede ser nula");
        }
        return search(query.question(), topK);
    }

    /**
     * Búsqueda restringida a un tipo de fuente (p. ej. solo ofertas) para preseleccionar candidatas antes de llamar al LLM.
     * La implementación por defecto filtra sobre un conjunto amplio; los adaptadores reales filtran antes de rankear.
     */
    default List<KnowledgeCandidate> searchByType(String query, int topK, KnowledgeChunk.SourceType type) {
        if (type == null) {
            throw new IllegalArgumentException("type no puede ser nulo");
        }
        return search(query, Math.max(topK * 10, 100)).stream()
                .filter(c -> c.chunk().sourceType() == type)
                .limit(topK)
                .toList();
    }
}
