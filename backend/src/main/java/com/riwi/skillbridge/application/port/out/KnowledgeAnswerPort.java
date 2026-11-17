package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import java.util.List;

/**
 * Puerto de salida para la generación de respuestas por parte del modelo generativo (LLM),
 * utilizando únicamente los fragmentos de conocimiento previamente validados.
 */
public interface KnowledgeAnswerPort {

    /**
     * Genera una respuesta redactada a partir de la pregunta del usuario y los fragmentos validados.
     *
     * @param question        la pregunta formulada por el usuario
     * @param validatedChunks fragmentos de conocimiento relevantes que superaron el umbral mínimo
     * @return texto de la respuesta redactada
     */
    String generateAnswer(String question, List<KnowledgeChunk> validatedChunks);

    /**
     * Sobrecarga conveniente que acepta un objeto de dominio KnowledgeQuery.
     *
     * @param query           objeto de consulta
     * @param validatedChunks fragmentos de conocimiento relevantes que superaron el umbral mínimo
     * @return texto de la respuesta redactada
     */
    default String generateAnswer(KnowledgeQuery query, List<KnowledgeChunk> validatedChunks) {
        if (query == null) {
            throw new IllegalArgumentException("query no puede ser nula");
        }
        return generateAnswer(query.question(), validatedChunks);
    }
}
