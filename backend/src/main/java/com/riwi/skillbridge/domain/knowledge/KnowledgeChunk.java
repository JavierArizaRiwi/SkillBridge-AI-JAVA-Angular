package com.riwi.skillbridge.domain.knowledge;

import java.util.Objects;

/**
 * Representa una unidad atómica de información pública indexada para búsqueda semántica y léxica.
 */
public record KnowledgeChunk(
        String sourceId,
        SourceType sourceType,
        String title,
        String content,
        Language language,
        String contentHash,
        String mode,
        Integer durationHours
) {
    public enum SourceType {
        OFFERING,
        CATEGORY,
        POLICY_DOC
    }

    public enum Language {
        ES,
        EN,
        MIXED
    }

    public KnowledgeChunk {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId no puede ser nulo ni estar vacío");
        }
        Objects.requireNonNull(sourceType, "sourceType no puede ser nulo");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title no puede ser nulo ni estar vacío");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content no puede ser nulo ni estar vacío");
        }
        Objects.requireNonNull(language, "language no puede ser nulo");
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash no puede ser nulo ni estar vacío");
        }
    }

    public KnowledgeChunk(
            String sourceId,
            SourceType sourceType,
            String title,
            String content,
            Language language,
            String contentHash
    ) {
        this(sourceId, sourceType, title, content, language, contentHash, null, null);
    }
}
