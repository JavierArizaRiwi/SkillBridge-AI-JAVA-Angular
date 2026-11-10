package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Servicio ofrecido por un Provider.
 *
 * <p>{@code mode} y {@code durationHours} describen la mentoría que se aprovisiona al aprobarse el pago
 * (ver docs/contracts/mentorships/events.md). Valores ausentes (ofertas anteriores o entradas de caché
 * antiguas) se normalizan a {@code ASYNCHRONOUS} y 1 hora.
 */
public record Offering(
        UUID id,
        UUID providerId,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active,
        MentorshipMode mode,
        int durationHours
) {
    public static final int DEFAULT_DURATION_HOURS = 1;
    public static final int MAX_DURATION_HOURS = 24;

    public Offering {
        if (providerId == null) throw new IllegalArgumentException("providerId es obligatorio");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title es obligatorio");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("price inválido");
        if (mode == null) mode = MentorshipMode.ASYNCHRONOUS;
        if (durationHours == 0) durationHours = DEFAULT_DURATION_HOURS;
        if (durationHours < 0 || durationHours > MAX_DURATION_HOURS) {
            throw new IllegalArgumentException("durationHours debe estar entre 1 y " + MAX_DURATION_HOURS);
        }
    }

    /** Compatibilidad: oferta con modalidad y duración por defecto. */
    public Offering(UUID id, UUID providerId, String title, String description,
                    String category, BigDecimal price, boolean active) {
        this(id, providerId, title, description, category, price, active, null, 0);
    }

    public static Offering create(UUID providerId, String title, String description,
                                  String category, BigDecimal price) {
        return create(providerId, title, description, category, price, null, null);
    }

    public static Offering create(UUID providerId, String title, String description,
                                  String category, BigDecimal price,
                                  MentorshipMode mode, Integer durationHours) {
        return new Offering(UUID.randomUUID(), providerId, title, description, category, price, true,
            mode, durationHours == null ? 0 : durationHours);
    }

    public Offering update(String title, String description, String category, BigDecimal price) {
        return update(title, description, category, price, null, null);
    }

    /** {@code mode}/{@code durationHours} nulos conservan el valor actual. */
    public Offering update(String title, String description, String category, BigDecimal price,
                           MentorshipMode mode, Integer durationHours) {
        return new Offering(id, providerId, title, description, category, price, active,
            mode == null ? this.mode : mode, durationHours == null ? this.durationHours : durationHours);
    }

    public Offering deactivate() {
        return new Offering(id, providerId, title, description, category, price, false, mode, durationHours);
    }

    public Offering activate() {
        return new Offering(id, providerId, title, description, category, price, true, mode, durationHours);
    }

    public boolean isOwnedBy(UUID userId) {
        return providerId.equals(userId);
    }
}
