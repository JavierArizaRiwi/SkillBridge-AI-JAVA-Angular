package com.riwi.skillbridge.infrastructure.adapter.out.persistence.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad JPA mapeada sobre la tabla knowledge_chunks en el schema knowledge.
 */
@Entity
@Table(name = "knowledge_chunks", schema = "knowledge")
public class KnowledgeChunkEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "mode", length = 20)
    private String mode;

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    @ColumnTransformer(write = "CAST(? AS vector)", read = "embedding::text")
    private String embedding;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeChunkEntity() {
    }

    public KnowledgeChunkEntity(
            UUID id,
            String sourceId,
            String sourceType,
            String title,
            String content,
            String language,
            String contentHash,
            String mode,
            Integer durationHours,
            String embedding,
            Instant updatedAt
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.title = title;
        this.content = content;
        this.language = language;
        this.contentHash = contentHash;
        this.mode = mode;
        this.durationHours = durationHours;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
    }

    public void update(
            String sourceType,
            String title,
            String content,
            String language,
            String contentHash,
            String mode,
            Integer durationHours,
            String embedding,
            Instant updatedAt
    ) {
        this.sourceType = sourceType;
        this.title = title;
        this.content = content;
        this.language = language;
        this.contentHash = contentHash;
        this.mode = mode;
        this.durationHours = durationHours;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(Integer durationHours) {
        this.durationHours = durationHours;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeChunkEntity that = (KnowledgeChunkEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
