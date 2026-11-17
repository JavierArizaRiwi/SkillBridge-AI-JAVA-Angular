package com.riwi.skillbridge.application.recommendation;

import java.util.UUID;

/**
 * Oferta recomendada. {@code offeringTitle} es opcional (puede ser nulo en consumidores antiguos): lo rellena
 * {@code AiRecommendationService} desde el catálogo activo para que la interfaz muestre el nombre de la mentoría.
 */
public record Recommendation(UUID offeringId, String offeringTitle, double score, String reason) {
    public Recommendation {
        if (offeringId == null) throw new IllegalArgumentException("offeringId es obligatorio");
        if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score debe estar entre 0.0 y 1.0");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason es obligatorio");
    }

    /** Compatibilidad: recomendación sin título. */
    public Recommendation(UUID offeringId, double score, String reason) {
        this(offeringId, null, score, reason);
    }
}
