package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Offering(
        UUID id,
        UUID providerId,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active
) {
    public Offering {
    if (providerId == null) throw new IllegalArgumentException("providerId es obligatorio");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title es obligatorio");
    if (price == null || price.signum() < 0) throw new IllegalArgumentException("price inválido");
}

    public static Offering create(UUID providerId, String title, String description,
                                  String category, BigDecimal price) {
        return new Offering(UUID.randomUUID(), providerId, title, description, category, price, true);
    }

    public Offering update(String title, String description, String category, BigDecimal price) {
        return new Offering(id, providerId, title, description, category, price, active);
    }

    public Offering deactivate() {
        return new Offering(id, providerId, title, description, category, price, false);
    }

    public Offering activate() {
        return new Offering(id, providerId, title, description, category, price, true);
    }

    public boolean isOwnedBy(UUID userId) {
        return providerId.equals(userId);
    }}
