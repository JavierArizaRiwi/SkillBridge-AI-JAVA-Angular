package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.model.Category;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, boolean active, Instant createdAt) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.id(), category.name(), category.active(), category.createdAt());
    }
}
