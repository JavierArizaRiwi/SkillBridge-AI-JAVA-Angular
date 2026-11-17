package com.riwi.skillbridge.infrastructure.adapter.out.persistence.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección de Spring Data JPA para resultados de búsqueda vectorial sobre el schema knowledge.
 */
public interface KnowledgeChunkSearchResult {

    UUID getId();

    String getSourceId();

    String getSourceType();

    String getTitle();

    String getContent();

    String getLanguage();

    String getContentHash();

    String getMode();

    Integer getDurationHours();

    Instant getUpdatedAt();

    Double getDistance();

    Double getSimilarity();
}
