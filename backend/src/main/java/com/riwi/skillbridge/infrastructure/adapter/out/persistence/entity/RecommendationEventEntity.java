package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.RecommendationInputType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the recommendation_events table.
 * Each row represents a single AI recommendation event (requested or generated).
 */
@Entity
@Table(name = "recommendation_events")
public class RecommendationEventEntity {

    @Id
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "offering_id")
    private UUID offeringId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false)
    private RecommendationInputType inputType;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RecommendationEventEntity() {}

    public RecommendationEventEntity(UUID id, UUID recommendationId, UUID userId,
                                     UUID offeringId, String eventType,
                                     RecommendationInputType inputType, String modelUsed,
                                     Long latencyMs, Instant occurredAt) {
        this.id = id;
        this.recommendationId = recommendationId;
        this.userId = userId;
        this.offeringId = offeringId;
        this.eventType = eventType;
        this.inputType = inputType;
        this.modelUsed = modelUsed;
        this.latencyMs = latencyMs;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getRecommendationId() { return recommendationId; }
    public UUID getUserId() { return userId; }
    public UUID getOfferingId() { return offeringId; }
    public String getEventType() { return eventType; }
    public RecommendationInputType getInputType() { return inputType; }
    public String getModelUsed() { return modelUsed; }
    public Long getLatencyMs() { return latencyMs; }
    public Instant getOccurredAt() { return occurredAt; }
}
