package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Category(UUID id, String name, boolean active, Instant createdAt) {
    public Category {
        if (id == null) throw new IllegalArgumentException("El id de la categoría es obligatorio");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        name = name.trim().toUpperCase();
    }
}
