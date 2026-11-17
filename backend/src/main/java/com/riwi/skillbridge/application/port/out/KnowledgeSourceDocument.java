package com.riwi.skillbridge.application.port.out;

/**
 * Representa un documento sin procesar proveniente de una fuente del corpus de conocimiento (HU-26).
 *
 * @param name    nombre del archivo o identificador del recurso
 * @param uri     URI del recurso
 * @param content contenido textual del documento en formato UTF-8
 */
public record KnowledgeSourceDocument(
        String name,
        String uri,
        String content
) {
}
