package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_requests")
public class ProviderRequestEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private ProviderRequestStatus status;
    private String message;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_reason")
    private String decisionReason;

    protected ProviderRequestEntity() {}

    public ProviderRequestEntity(UUID id, UUID userId, ProviderRequestStatus status, String message,
                                 Instant createdAt, UUID decidedBy, Instant decidedAt, String decisionReason) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionReason = decisionReason;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public ProviderRequestStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason; }
}
