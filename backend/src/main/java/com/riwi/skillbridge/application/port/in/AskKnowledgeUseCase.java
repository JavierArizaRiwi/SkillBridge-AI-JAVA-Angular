package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;

/**
 * Caso de uso para la consulta del asistente de conocimiento (RAG).
 */
public interface AskKnowledgeUseCase {

    /**
     * Procesa una consulta de conocimiento y genera una respuesta fundamentada en fuentes públicas.
     *
     * @param query objeto que encapsula la pregunta del usuario y sus metadatos
     * @return respuesta generada que incluye estado de suficiencia y fuentes citadas
     */
    KnowledgeAnswer ask(KnowledgeQuery query);

    /**
     * Sobrecarga conveniente que acepta la pregunta en texto plano.
     *
     * @param question texto de la pregunta
     * @return respuesta generada
     */
    default KnowledgeAnswer ask(String question) {
        return ask(new KnowledgeQuery(question));
    }
}
