package com.riwi.skillbridge.application.port.out;

import java.util.List;

/**
 * Puerto de salida para la carga de documentos fuente del corpus de conocimiento (HU-26).
 */
public interface KnowledgeCorpusSourcePort {

    /**
     * Carga los documentos fuente disponibles en la fuente configurada.
     *
     * @return lista de documentos fuente
     */
    List<KnowledgeSourceDocument> loadDocuments();
}
