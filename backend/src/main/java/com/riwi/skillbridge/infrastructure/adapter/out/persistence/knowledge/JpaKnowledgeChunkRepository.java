package com.riwi.skillbridge.infrastructure.adapter.out.persistence.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para entidades KnowledgeChunkEntity.
 * Accede y opera exclusivamente sobre el schema knowledge (tabla knowledge.knowledge_chunks).
 */
public interface JpaKnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {

    Optional<KnowledgeChunkEntity> findBySourceId(String sourceId);

    Optional<KnowledgeChunkEntity> findBySourceIdAndLanguage(String sourceId, String language);

    List<KnowledgeChunkEntity> findByContentHash(String contentHash);

    List<KnowledgeChunkEntity> findAllByContentHash(String contentHash);

    boolean existsBySourceId(String sourceId);

    boolean existsByContentHash(String contentHash);

    void deleteBySourceId(String sourceId);

    void deleteBySourceIdAndLanguage(String sourceId, String language);

    /** Solo identidad y hash (sin proyectar el vector): para decidir qué recalcular. */
    @Query(value = "SELECT c.source_id AS sourceId, c.language AS language, c.content_hash AS contentHash FROM knowledge.knowledge_chunks c", nativeQuery = true)
    List<IndexedHashRow> findAllIndexedHashes();

    interface IndexedHashRow {
        String getSourceId();

        String getLanguage();

        String getContentHash();
    }

    void deleteByContentHash(String contentHash);

    /**
     * Consulta nativa sobre el schema knowledge para obtener todos los content_hash existentes.
     */
    @Query(value = "SELECT c.content_hash FROM knowledge.knowledge_chunks c", nativeQuery = true)
    Set<String> findAllContentHashes();

    /**
     * Consulta nativa sobre el schema knowledge para buscar los fragmentos más cercanos usando similitud coseno.
     * Ordena por embedding <=> CAST(:queryEmbedding AS vector) y calcula similitud como 1 - distancia.
     * No proyecta la columna embedding.
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.source_id AS sourceId,
                   c.source_type AS sourceType,
                   c.title AS title,
                   c.content AS content,
                   c.language AS language,
                   c.content_hash AS contentHash,
                   c.mode AS mode,
                   c.duration_hours AS durationHours,
                   c.updated_at AS updatedAt,
                   (c.embedding <=> CAST(:queryEmbedding AS vector)) AS distance,
                   (1.0 - (c.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity
            FROM knowledge.knowledge_chunks c
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector) ASC
            LIMIT :topK
            """, nativeQuery = true)
    List<KnowledgeChunkSearchResult> searchNearest(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK);

    /**
     * Consulta nativa sobre el schema knowledge para vaciar la tabla durante el reemplazo atómico.
     */
    @Modifying
    @Query(value = "DELETE FROM knowledge.knowledge_chunks", nativeQuery = true)
    void deleteAllChunks();
}
