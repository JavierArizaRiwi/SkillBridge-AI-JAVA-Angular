package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CategoryEntity() {}

    public CategoryEntity(UUID id, String name, boolean active, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
