package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Puerto de salida para la persistencia e indexación de fragmentos de conocimiento
 * (upsert por contentHash, reemplazo atómico del índice y lectura de hashes existentes).
 */
public interface KnowledgeIndexPort {

    /**
     * Lee y retorna el conjunto de todos los contentHash existentes en el almacén de conocimiento.
     *
     * @return conjunto de hashes existentes
     */
    Set<String> findExistingHashes();

    /**
     * Inserta o actualiza un fragmento por su contentHash junto con su vector de embedding.
     *
     * @param chunk     fragmento de conocimiento a indexar
     * @param embedding vector numérico del embedding del fragmento
     */
    void upsert(KnowledgeChunk chunk, double[] embedding);

    /**
     * Reemplaza de forma atómica la totalidad del índice con la lista de fragmentos indexados.
     *
     * @param chunks lista de fragmentos con sus respectivos vectores de embedding
     */
    void replaceIndex(List<IndexedChunk> chunks);

    /**
     * Hash de contenido de cada fragmento indexado, por (sourceId, idioma). Permite saber qué fragmentos ya tienen
     * vector vigente sin volver a calcularlo (FR-018).
     */
    java.util.Map<IndexKey, String> findIndexedHashes();

    /**
     * Aplica de forma atómica (una sola transacción) los fragmentos nuevos o cambiados y las eliminaciones: una lectura
     * concurrente nunca ve un índice a medias (FR-015).
     *
     * @param upserts   fragmentos con su vector ya calculado
     * @param deletions claves (sourceId, idioma) que ya no deben estar en el índice
     */
    void applyChanges(List<IndexedChunk> upserts, java.util.Collection<IndexKey> deletions);

    /** Elimina todos los fragmentos (cualquier idioma) de una fuente, p. ej. una oferta desactivada. No falla si no existe. */
    void deleteBySourceId(String sourceId);

    /** Identidad de un fragmento indexado: una fuente puede tener un fragmento por idioma. */
    record IndexKey(String sourceId, KnowledgeChunk.Language language) {
        public IndexKey {
            Objects.requireNonNull(sourceId, "sourceId no puede ser nulo");
            Objects.requireNonNull(language, "language no puede ser nulo");
        }
    }

    /**
     * Sobrecarga conveniente para reemplazo atómico a partir de listas paralelas de fragmentos y vectores.
     *
     * @param chunks     lista de fragmentos de conocimiento
     * @param embeddings lista paralela de vectores de embedding
     */
    default void replaceIndex(List<KnowledgeChunk> chunks, List<double[]> embeddings) {
        Objects.requireNonNull(chunks, "chunks no puede ser nulo");
        Objects.requireNonNull(embeddings, "embeddings no puede ser nulo");
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks y embeddings deben tener el mismo tamaño");
        }
        List<IndexedChunk> indexed = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            indexed.add(new IndexedChunk(chunks.get(i), embeddings.get(i)));
        }
        replaceIndex(indexed);
    }

    /**
     * Inserta o actualiza un fragmento empaquetado en un IndexedChunk.
     *
     * @param chunk fragmento con embedding
     */
    default void upsert(IndexedChunk chunk) {
        Objects.requireNonNull(chunk, "chunk no puede ser nulo");
        upsert(chunk.chunk(), chunk.embedding());
    }

    /**
     * Inserta o actualiza un lote de fragmentos indexados.
     *
     * @param chunks lista de fragmentos con embedding
     */
    default void upsertAll(List<IndexedChunk> chunks) {
        Objects.requireNonNull(chunks, "chunks no puede ser nulo");
        chunks.forEach(this::upsert);
    }

    /**
     * Verifica si existe un fragmento con el hash de contenido especificado.
     *
     * @param contentHash hash a verificar
     * @return true si el hash ya está indexado, false de lo contrario
     */
    default boolean existsByContentHash(String contentHash) {
        return findExistingHashes().contains(contentHash);
    }

    /**
     * Registro auxiliar inmutable que asocia un KnowledgeChunk con su vector de embedding.
     */
    record IndexedChunk(KnowledgeChunk chunk, double[] embedding) {
        public IndexedChunk {
            Objects.requireNonNull(chunk, "chunk no puede ser nulo");
            Objects.requireNonNull(embedding, "embedding no puede ser nulo");
        }
    }
}
